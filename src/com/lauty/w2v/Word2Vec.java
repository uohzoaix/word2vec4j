package com.lauty.w2v;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.RandomAccessFile;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Random;

public class Word2Vec {

	private static final Integer MAX_STRING = 100;
	private static final Integer EXP_TABLE_SIZE = 1000;
	private static final Integer MAX_EXP = 6;
	private static final Integer MAX_SENTENCE_LENGTH = 1000;
	private static final Integer MAX_CODE_LENGTH = 40;
	private static final Integer VOCAB_HASH_SIZE = 30000000;

	public static class VocabWord {
		Integer cn = 0;
		Integer[] point = new Integer[MAX_CODE_LENGTH];
		Byte[] code = new Byte[MAX_CODE_LENGTH];
		String word = "";
		Integer codeLen = 0;

		public Integer getCn() {
			return cn;
		}

		public void setCn(Integer cn) {
			this.cn = cn;
		}

		public Integer[] getPoint() {
			return point;
		}

		public void setPoint(Integer[] point) {
			this.point = point;
		}

		public String getWord() {
			return word;
		}

		public void setWord(String word) {
			this.word = word;
		}

		public Byte[] getCode() {
			return code;
		}

		public void setCode(Byte[] code) {
			this.code = code;
		}

		public Integer getCodeLen() {
			return codeLen;
		}

		public void setCodeLen(Integer codeLen) {
			this.codeLen = codeLen;
		}

		@Override
		public String toString() {
			return getWord() + "," + getCn() + "," + getCodeLen() + "," + Arrays.deepToString(getPoint()) + "," + Arrays.deepToString(getCode());
		}

	}

	static VocabWord[] vocabs;

	static int[] table, vocabHash;
	private static String trainFile = "", outputFile = "", saveVocabFile = "", readVocabFile = "";
	static int classes = 0, hs = 1, negative = 0, binary = 0, cbow = 0, debug_mode = 2, window = 5, min_count = 5, num_threads = 1, min_reduce = 1, vocabMaxSize = 1000, vocabSize = 0,
			layer1Size = 100;
	static long trainWords = 0, wordCountActual = 0, fileSize = 0, start = System.currentTimeMillis();
	static double alpha = 0.025, startingAlpha, sample = 0, tableSize = 1e8;
	static double[] syn0, syn1, syn1neg;
	static double[] expTable;

	public static void initWV() {
		vocabs = new VocabWord[vocabMaxSize];
		for (int i = 0; i < vocabMaxSize; i++) {
			vocabs[i] = new VocabWord();
		}
		vocabHash = new int[VOCAB_HASH_SIZE];
		expTable = new double[EXP_TABLE_SIZE];
		for (int i = 0; i < EXP_TABLE_SIZE; i++) {
			expTable[i] = Math.exp(((i / (double) EXP_TABLE_SIZE * 2 - 1) * MAX_EXP));// Precompute the exp() table
			expTable[i] = expTable[i] / (expTable[i] + 1);// Precompute f(x) = x / (x + 1)
		}
	}

	public static void initUniGramTable() {
		int a, i;
		long trainWordsPow = 0;
		double d1, power = 0.75f;
		table = new int[(int) (tableSize * 8)];
		for (a = 0; a < vocabSize; a++) {
			trainWordsPow += Math.pow(vocabs[a].getCn(), power);
		}
		i = 0;
		d1 = Math.pow(vocabs[i].getCn(), power) / trainWordsPow;
		for (a = 0; a < tableSize; a++) {
			table[a] = i;
			if (a / tableSize > d1) {
				i++;
				d1 += Math.pow(vocabs[i].getCn(), power) / trainWordsPow;
			}
			if (i >= vocabSize)
				i = (int) (vocabSize - 1);
		}
	}

	// Returns hash value of a word
	public static int getWordHash(String word) {
		int a;
		BigInteger hash = BigInteger.ZERO;
		byte[] wordBytes = word.getBytes();
		for (a = 0; a < wordBytes.length; a++) {
			if (wordBytes[a] != 0) {
				hash = hash.multiply(new BigInteger(257 + "")).add(new BigInteger(wordBytes[a] + ""));
			}
		}
		hash = hash.mod(new BigInteger(VOCAB_HASH_SIZE + ""));
		return hash.intValue();
	}

	// Returns position of a word in the vocabulary; if the word is not found, returns -1
	public static int searchVocab(String word) {
		int hash = getWordHash(word);
		while (true) {
			try {
				if (vocabHash[hash] == -1)
					return -1;
				try {
					if (word.equals(vocabs[vocabHash[hash]].getWord())) {
						return vocabHash[hash];
					}
				} catch (Exception e) {
					e.printStackTrace();
				}
				hash = (hash + 1) % VOCAB_HASH_SIZE;
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

	// Adds a word to the vocabulary
	public static int addWordToVocab(String word) {
		int hash = 0, length = word.length();
		if (length > MAX_STRING) {
			word = word.substring(0, MAX_STRING);
		}
		vocabs[vocabSize].setWord(word);
		vocabs[vocabSize].setCn(1);
		vocabSize++;
		// Reallocate memory if needed
		if (vocabSize + 2 >= vocabMaxSize) {
			vocabMaxSize += 1000;
			VocabWord[] newvocabs = new VocabWord[vocabMaxSize];
			System.arraycopy(vocabs, 0, newvocabs, 0, vocabMaxSize - 1000);
			for (int i = vocabMaxSize - 1000; i < vocabMaxSize; i++) {
				newvocabs[i] = new VocabWord();
			}
			vocabs = newvocabs;
			//vocabs = (struct vocab_word *)realloc(vocab, vocab_max_size * sizeof(struct vocab_word));
		}
		hash = getWordHash(word);
		while (vocabHash[hash] != -1)
			hash = (hash + 1) % VOCAB_HASH_SIZE;
		vocabHash[hash] = vocabSize - 1;
		return vocabSize - 1;
	}

	public int vocabCompare(VocabWord a, VocabWord b) {
		return b.getCn() - a.getCn();
	}

	// Sorts the vocabulary by frequency using word counts
	public static void sortVocab() {
		List<VocabWord> vocabList = Arrays.asList(vocabs);
		vocabList.remove(null);
		vocabs = (VocabWord[]) vocabList.toArray();
		int a, size;
		int hash;
		// Sort the vocabulary and keep </s> at the first position
		Comparator<VocabWord> comparator = new Comparator<Word2Vec.VocabWord>() {
			@Override
			public int compare(VocabWord o1, VocabWord o2) {
				return o2.getCn() - o1.getCn();
			}
		};
		Arrays.sort(vocabs, 1, vocabs.length, comparator);
		for (a = 0; a < VOCAB_HASH_SIZE; a++)
			vocabHash[a] = -1;
		size = vocabSize;
		trainWords = 0;
		for (a = 0; a < size; a++) {
			// Words occuring less than min_count times will be discarded from the vocab
			if (vocabs[a].getCn() < min_count) {
				vocabSize--;
				vocabs[vocabSize].setWord("");
			} else {
				// Hash will be re-computed, as after the sorting it is not actual
				hash = getWordHash(vocabs[a].getWord());
				while (vocabHash[hash] != -1)
					hash = (hash + 1) % VOCAB_HASH_SIZE;
				vocabHash[hash] = a;
				trainWords += vocabs[a].getCn();
			}
		}
		//vocabs = new VocabWord[vocabSize + 1];
		// vocab = (struct vocab_word *)realloc(vocab, (vocab_size + 1) * sizeof(struct vocab_word));
		// Allocate memory for the binary tree construction
		//		for (a = 0; a < vocabSize; a++) {
		//			vocabs[a].setCode(new byte[MAX_CODE_LENGTH]);
		//			vocabs[a].setPoint(new int[MAX_CODE_LENGTH]);
		//		}
	}

	// Reduces the vocabulary by removing infrequent tokens
	public static void reduceVocab() {
		int a, b = 0;
		int hash;
		for (a = 0; a < vocabSize; a++)
			if (vocabs[a].cn > min_reduce) {
				vocabs[b].setCn(vocabs[a].getCn());
				vocabs[b].setWord(vocabs[a].getWord());
				b++;
			} else
				vocabs[a].setWord("");
		vocabSize = b;
		for (a = 0; a < VOCAB_HASH_SIZE; a++)
			vocabHash[a] = -1;
		for (a = 0; a < vocabSize; a++) {
			// Hash will be re-computed, as it is not actual
			hash = getWordHash(vocabs[a].getWord());
			while (vocabHash[hash] != -1)
				hash = (hash + 1) % VOCAB_HASH_SIZE;
			vocabHash[hash] = a;
		}
		min_reduce++;
	}

	// Create binary Huffman tree using the word counts
	// Frequent words will have short uniqe binary codes
	public static void createBinaryTree() {
		int a, b, i, min1i, min2i, pos1, pos2;
		int[] point = new int[MAX_CODE_LENGTH];
		byte[] code = new byte[MAX_CODE_LENGTH];
		int[] count = new int[vocabSize * 2 + 1];
		byte[] binary = new byte[vocabSize * 2 + 1];
		int[] parentNode = new int[vocabSize * 2 + 1];
		for (a = 0; a < vocabSize; a++)
			count[a] = vocabs[a].getCn();
		for (a = vocabSize; a < vocabSize * 2; a++)
			count[a] = (int) 1e15;
		pos1 = vocabSize - 1;
		pos2 = vocabSize;
		// Following algorithm constructs the Huffman tree by adding one node at a time
		for (a = 0; a < vocabSize - 1; a++) {
			// First, find two smallest nodes 'min1, min2'
			if (pos1 >= 0) {
				if (count[pos1] < count[pos2]) {
					min1i = pos1;
					pos1--;
				} else {
					min1i = pos2;
					pos2++;
				}
			} else {
				min1i = pos2;
				pos2++;
			}
			if (pos1 >= 0) {
				if (count[pos1] < count[pos2]) {
					min2i = pos1;
					pos1--;
				} else {
					min2i = pos2;
					pos2++;
				}
			} else {
				min2i = pos2;
				pos2++;
			}
			count[vocabSize + a] = count[min1i] + count[min2i];
			parentNode[min1i] = vocabSize + a;
			parentNode[min2i] = vocabSize + a;
			binary[min2i] = 1;
		}
		// Now assign binary code to each vocabulary word
		for (a = 0; a < vocabSize; a++) {
			b = a;
			i = 0;
			while (true) {
				code[i] = binary[b];
				point[i] = b;
				i++;
				b = parentNode[b];
				if (b == vocabSize * 2 - 2)
					break;
			}
			vocabs[a].setCodeLen(i);
			vocabs[a].point[0] = vocabSize - 2;
			for (b = 0; b < i; b++) {
				vocabs[a].code[i - b - 1] = code[b];
				vocabs[a].point[i - b] = point[b] - vocabSize;
			}
			//System.out.println(vocabs[a]);
		}
		count = null;
		binary = null;
		parentNode = null;
	}

	public static void learnVocabFromTrainFile() throws FileNotFoundException {
		BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(trainFile)));
		try {
			int a, i;
			for (a = 0; a < VOCAB_HASH_SIZE; a++)
				vocabHash[a] = -1;
			vocabSize = 0;
			addWordToVocab("</s>");
			String temp = null;
			int num = 0;
			while ((temp = br.readLine()) != null) {
				String[] strs = temp.split(" ");
				trainWords += strs.length;
				if ((debug_mode > 1) && (trainWords % 100000 == 0)) {
					//System.out.println(trainWords / 1000 + "K," + vocabSize);
					System.out.printf("%dK %d%c", trainWords / 1000, vocabSize, 13);
				}
				for (String str : strs) {
					i = searchVocab(str);
					if (i == -1) {
						try {
							addWordToVocab(str);
							//vocabs[a].setCn(1);
						} catch (Exception e) {
							e.printStackTrace();
						}
					} else
						vocabs[i].setCn(vocabs[i].getCn() + 1);
					if (vocabSize > VOCAB_HASH_SIZE * 0.7)
						reduceVocab();
				}
				num++;
				//i = searchVocab("</s>");
				//vocabs[i].setCn(vocabs[i].getCn() + 1);
			}
			i = searchVocab("</s>");
			vocabs[i].setCn(num - 2);
			System.out.println(new Date() + ",开始sort,vocab_size=" + vocabSize);
			sortVocab();
			System.out.println(new Date() + ",结束sort,vocab_size=" + vocabSize);
			if (debug_mode > 0) {
				System.out.println(String.format("Vocab size: %d\n", vocabSize));
				System.out.println(String.format("Words in train file: %d\n", trainWords));
			}
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			if (br != null) {
				try {
					br.close();
				} catch (Exception e2) {
				}
			}
		}
	}

	public static void saveVocab() {
		RandomAccessFile raf = null;
		try {
			raf = new RandomAccessFile(saveVocabFile, "wb");
			for (int i = 0; i < vocabSize; i++) {
				raf.writeBytes(String.format("%s %d\n", vocabs[i].getWord(), vocabs[i].getCn()));
			}
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			if (raf != null) {
				try {
					raf.close();
				} catch (Exception e2) {
				}
			}
		}
	}

	public static void readVocab() throws FileNotFoundException {
		BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(trainFile)));
		try {
			int a = 0;
			for (a = 0; a < VOCAB_HASH_SIZE; a++)
				vocabHash[a] = -1;
			vocabSize = 0;
			String temp = null;
			while ((temp = br.readLine()) != null) {
				String[] strs = temp.split(" ");
				for (String str : strs) {
					a = addWordToVocab(str);
				}
			}
			sortVocab();
			if (debug_mode > 0) {
				System.out.printf("Vocab size: %d\n", vocabSize);
				System.out.printf("Words in train file: %d\n", trainWords);
			}
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			if (br != null) {
				try {
					br.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}

	}

	public static void initNet() {
		try {
			int a, b;
			syn0 = new double[vocabSize * layer1Size];
			//syn0 = new double[vocabSize][layer1Size];
			if (hs != 0) {
				syn1 = new double[vocabSize * layer1Size];
				for (b = 0; b < layer1Size; b++)
					for (a = 0; a < vocabSize; a++)
						//syn1[a][b] = 0;
						syn1[a * layer1Size + b] = 0;
			}
			if (negative > 0) {
				syn1neg = new double[vocabSize * layer1Size];
				for (b = 0; b < layer1Size; b++)
					for (a = 0; a < vocabSize; a++)
						//syn1neg[a][b] = 0;
						syn1neg[a * layer1Size + b] = 0;
			}
			Random random = new Random();
			for (b = 0; b < layer1Size; b++)
				for (a = 0; a < vocabSize; a++)
					//syn0[a][b] = (float) ((random.nextFloat() - 0.5) / layer1Size);
					syn0[a * layer1Size + b] = (random.nextDouble() - 0.5) / layer1Size;
			createBinaryTree();
		} catch (Exception e) {
			System.out.println(new Date() + "-" + vocabSize);
			e.printStackTrace();
		}
	}

	public static class TrainModelThread extends Thread {
		final long id;

		public TrainModelThread(long id) {
			this.id = id;
		}

		@Override
		public void run() {
			long next_random = id;
			int b, word, sentence_length = 0, sentence_position = 0, word_count = 0, last_word_count = 0;
			int[] sen = new int[MAX_SENTENCE_LENGTH + 1];
			long now = System.currentTimeMillis();
			BufferedReader br = null;
			try {
				br = new BufferedReader(new InputStreamReader(new FileInputStream(trainFile)));
			} catch (FileNotFoundException e2) {
				e2.printStackTrace();
			}
			try {
				String temp = null;
				while ((temp = br.readLine()) != null) {
					if (word_count - last_word_count > 10000) {
						wordCountActual += word_count - last_word_count;
						last_word_count = word_count;
						if ((debug_mode > 1)) {
							now = System.currentTimeMillis();
							System.out.printf("%cAlpha: %f  Progress: %.2f%%  wordCountActual: %d  Words/thread/sec: %.2fk  ", 13, alpha, wordCountActual / (float) (trainWords + 1) * 100,
									wordCountActual, wordCountActual / ((float) (now - start + 1) / 1000));
						}
						alpha = startingAlpha * (1 - wordCountActual / (trainWords + 1));
						if (alpha < startingAlpha * 0.0001)
							alpha = startingAlpha * 0.0001;
					}
					String[] strs = temp.split(" ");
					word_count += strs.length;
					for (String str : strs) {
						word = searchVocab(str);
						if (word == -1)
							continue;
						if (word == 0)
							break;
						// The subsampling randomly discards frequent words while keeping the ranking same
						if (sample > 0) {
							double ran = (Math.sqrt(vocabs[word].getCn() / (sample * trainWords)) + 1) * (sample * trainWords) / vocabs[word].getCn();
							next_random = next_random * 25214903917l + 11;
							if (ran < (next_random & 0xFFFF) / 65536)
								continue;
						}
						sen[sentence_length] = word;
						sentence_length++;
						if (sentence_length >= MAX_SENTENCE_LENGTH)
							break;
					}
					if (word_count > trainWords / num_threads)
						break;
					for (int index = 0; index < sen.length; index++) {
						word = sen[index];
						if (word == -1)
							continue;
						next_random = next_random * 25214903917L + 11;
						b = (int) (next_random % window);
						if (cbow != 0) { //train the cbow architecture
							cbow(sen, b, index, word, next_random);
						} else { //train skip-gram
							skip(sen, b, index, word, next_random);
						}
					}
					sentence_length = 0;
				}
			} catch (FileNotFoundException e1) {
				e1.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			} finally {
				if (br != null) {
					try {
						br.close();
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
			}
		}
	}

	public static void cbow(int[] sen, int b, int sentence_position, int word, long next_random) {
		double[] neu1 = new double[layer1Size], neu1e = new double[layer1Size];
		int sentence_length = sen.length;
		int l2, target;
		long label;
		double f, g;
		// in -> hidden
		for (int a = b; a < window * 2 + 1 - b; a++) {
			if (a != window) {
				int c = sentence_position - window + a;
				if (c < 0)
					continue;
				if (c >= sentence_length)
					continue;
				int last_word = sen[c];
				if (last_word == -1)
					continue;
				for (c = 0; c < layer1Size; c++)
					//neu1[c] += syn0[last_word][c];
					neu1[c] += syn0[c + last_word * layer1Size];
			}
		}
		if (hs != 0) {
			for (int d = 0; d < vocabs[word].getCodeLen(); d++) {
				f = 0;
				l2 = vocabs[word].getPoint()[d] * layer1Size;
				// Propagate hidden -> output
				for (int c = 0; c < layer1Size; c++)
					//f += neu1[c] * syn1[l2][c];
					f += neu1[c] * syn1[c + l2];
				if (f <= -MAX_EXP)
					continue;
				else if (f >= MAX_EXP)
					continue;
				else
					f = expTable[(int) ((f + MAX_EXP) * (EXP_TABLE_SIZE / MAX_EXP / 2))];
				// 'g' is the gradient multiplied by the learning rate
				g = (1 - vocabs[word].getCode()[d] - f) * alpha;
				// Propagate errors output -> hidden
				for (int c = 0; c < layer1Size; c++)
					//neu1e[c] += g * syn1[l2][c];
					neu1e[c] += g * syn1[c + l2];
				// Learn weights hidden -> output
				for (int c = 0; c < layer1Size; c++)
					//syn1[l2][c] += g * neu1[c];
					syn1[c + l2] += g * neu1[c];
			}
		}
		// NEGATIVE SAMPLING
		if (negative > 0) {
			for (int d = 0; d < negative + 1; d++) {
				if (d == 0) {
					target = word;
					label = 1;
				} else {
					next_random = next_random * 25214903917l + 11;
					target = table[(int) ((next_random >> 16) % tableSize)];
					if (target == 0)
						target = (int) (next_random % (vocabSize - 1) + 1);
					if (target == word)
						continue;
					label = 0;
				}
				l2 = target * layer1Size;
				f = 0;
				for (int c = 0; c < layer1Size; c++)
					//f += neu1[c] * syn1neg[l2][c];
					f += neu1[c] * syn1neg[c + l2];
				if (f > MAX_EXP)
					g = (label - 1) * alpha;
				else if (f < -MAX_EXP)
					g = (label - 0) * alpha;
				else
					g = (label - expTable[(int) ((f + MAX_EXP) * (EXP_TABLE_SIZE / MAX_EXP / 2))]) * alpha;
				for (int c = 0; c < layer1Size; c++)
					//neu1e[c] += g * syn1neg[l2][c];
					neu1e[c] += g * syn1neg[c + l2];
				for (int c = 0; c < layer1Size; c++)
					//syn1neg[l2][c] += g * neu1[c];
					syn1neg[c + l2] += g * neu1[c];
			}
		}
		// hidden -> in
		for (int a = b; a < window * 2 + 1 - b; a++) {
			if (a != window) {
				int c = sentence_position - window + a;
				if (c < 0)
					continue;
				if (c >= sentence_length)
					continue;
				int last_word = sen[c];
				if (last_word == -1)
					continue;
				for (c = 0; c < layer1Size; c++)
					//syn0[last_word][c] += neu1e[c];
					syn0[c + last_word * layer1Size] += neu1e[c];
			}
		}
		neu1 = null;
		neu1e = null;
	}

	public static void skip(int[] sen, int b, int sentence_position, int word, long next_random) {
		int l1, l2, target;
		double f, g;
		long label;
		int sentence_length = sen.length;
		double[] neu1e = new double[layer1Size];
		for (int a = b; a < window * 2 + 1 - b; a++) {
			if (a != window) {
				int c = sentence_position - window + a;
				if (c < 0 || c >= sentence_length)
					continue;
				Arrays.fill(neu1e, 0);
				int last_word = sen[c];
				if (last_word == -1)
					continue;
				if (vocabs[word].equals("过年")) {
					System.out.println(vocabs[word].getCodeLen());
					System.out.println(Arrays.deepToString(vocabs[word].point));
				}
				l1 = last_word * layer1Size;
				// HIERARCHICAL SOFTMAX
				if (hs != 0) {
					for (int d = 0; d < vocabs[word].getCodeLen(); d++) {
						f = 0;
						l2 = vocabs[word].point[d] * layer1Size;
						// Propagate hidden -> output
						for (c = 0; c < layer1Size; c++)
							//f += syn0[l1][c] * syn1[l2][c];
							f += syn0[c + l1] * syn1[c + l2];
						if (f <= -MAX_EXP || f >= MAX_EXP)
							continue;
						else
							f = expTable[(int) ((f + MAX_EXP) * (EXP_TABLE_SIZE / MAX_EXP / 2))];
						// 'g' is the gradient multiplied by the learning rate
						g = (1 - vocabs[word].code[d] - f) * alpha;
						// Propagate errors output -> hidden
						for (c = 0; c < layer1Size; c++)
							//neu1e[c] += g * syn1[l2][c];
							neu1e[c] += g * syn1[c + l2];
						// Learn weights hidden -> output
						for (c = 0; c < layer1Size; c++)
							//syn1[l2][c] += g * syn0[l1][c];
							syn1[c + l2] += g * syn0[c + l1];
					}
				}
				// NEGATIVE SAMPLING
				if (negative > 0) {
					for (int d = 0; d < negative + 1; d++) {
						if (d == 0) {
							target = word;
							label = 1;
						} else {
							next_random = next_random * 25214903917l + 11;
							target = table[(int) ((next_random >> 16) % tableSize)];
							if (target == 0)
								target = (int) (next_random % (vocabSize - 1) + 1);
							if (target == word)
								continue;
							label = 0;
						}
						l2 = target * layer1Size;
						f = 0;
						for (c = 0; c < layer1Size; c++)
							//f += syn0[l1][c] * syn1neg[l2][c];
							f += syn0[c + l1] * syn1neg[c + l2];
						if (f > MAX_EXP)
							g = (label - 1) * alpha;
						else if (f < -MAX_EXP)
							g = (label - 0) * alpha;
						else
							g = (label - expTable[(int) ((f + MAX_EXP) * (EXP_TABLE_SIZE / MAX_EXP / 2))]) * alpha;
						for (c = 0; c < layer1Size; c++)
							//neu1e[c] += g * syn1neg[l2][c];
							neu1e[c] += g * syn1neg[c + l2];
						for (c = 0; c < layer1Size; c++)
							//syn1neg[l2][c] += g * syn0[l1][c];
							syn1neg[c + l2] += g * syn0[c + l1];
					}
				}
				// Learn weights input -> hidden
				for (c = 0; c < layer1Size; c++) {
					//syn0[l1][c] += neu1e[c];
					syn0[c + l1] += neu1e[c];
				}
			}
		}
		neu1e = null;
	}

	public static void trainModel() {
		int a, b, c, d;
		DataOutputStream dos = null;
		try {
			System.out.printf("Starting training using file %s\n", trainFile);
			startingAlpha = alpha;
			if (!readVocabFile.equals(""))
				readVocab();
			else
				learnVocabFromTrainFile();
			if (!readVocabFile.equals(""))
				saveVocab();
			if (outputFile.equals(""))
				return;
			initNet();
			if (negative > 0)
				initUniGramTable();
			start = System.currentTimeMillis();
			for (a = 0; a < num_threads; a++) {
				TrainModelThread trainThread = new TrainModelThread(a);
				trainThread.start();
				trainThread.join();
			}
			dos = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(outputFile)));
			if (classes == 0) {
				// Save the word vectors
				dos.writeInt(vocabSize);
				dos.writeInt(layer1Size);
				//dos.writeUTF(String.format("%d %d\n", vocabSize, layer1Size));
				for (a = 0; a < vocabSize; a++) {
					String word = vocabs[a].getWord();
					dos.writeUTF(word);
					if (binary != 0)
						for (b = 0; b < layer1Size; b++) {
							if (word.equals("发布")) {
								System.out.println(word + "," + a);
								System.out.println(syn0[a * layer1Size + b]);
							}
							dos.writeDouble(syn0[a * layer1Size + b]);
						}
					else
						for (b = 0; b < layer1Size; b++)
							dos.writeUTF(syn0[a * layer1Size + b] + "");
					//dos.writeUTF("\n");
				}
			} else {
				// Run K-means on the word vectors
				int clcn = classes, iter = 10, closeid;
				int[] centcn = new int[classes];
				int[] cl = new int[vocabSize];
				double closev, x;
				double[] cent = new double[classes * layer1Size];
				for (a = 0; a < vocabSize; a++)
					cl[a] = a % clcn;
				for (a = 0; a < iter; a++) {
					for (b = 0; b < clcn * layer1Size; b++)
						cent[b] = 0;
					for (b = 0; b < clcn; b++)
						centcn[b] = 1;
					for (c = 0; c < vocabSize; c++) {
						for (d = 0; d < layer1Size; d++)
							cent[layer1Size * cl[c] + d] += syn0[c * layer1Size + d];
						centcn[cl[c]]++;
					}
					for (b = 0; b < clcn; b++) {
						closev = 0;
						for (c = 0; c < layer1Size; c++) {
							cent[layer1Size * b + c] /= centcn[b];
							closev += cent[layer1Size * b + c] * cent[layer1Size * b + c];
						}
						closev = Math.sqrt(closev);
						for (c = 0; c < layer1Size; c++)
							cent[layer1Size * b + c] /= closev;
					}
					for (c = 0; c < vocabSize; c++) {
						closev = -10;
						closeid = 0;
						for (d = 0; d < clcn; d++) {
							x = 0;
							for (b = 0; b < layer1Size; b++)
								x += cent[layer1Size * d + b] * syn0[c * layer1Size + b];
							if (x > closev) {
								closev = x;
								closeid = d;
							}
						}
						cl[c] = closeid;
					}
				}
				// Save the K-means classes
				for (a = 0; a < vocabSize; a++)
					dos.writeUTF(String.format("%s %d\n", new String(vocabs[a].getWord()), cl[a]));
				centcn = null;
				cent = null;
				cl = null;
			}
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			if (dos != null) {
				try {
					dos.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
	}

	public static int argPos(String str, int num, String[] args) {
		int a;
		for (a = 0; a < num; a++)
			if (str.equals(args[a])) {
				if (a == num - 1) {
					System.out.printf("Argument missing for %s\n", str);
					System.exit(1);
				}
				return a;
			}
		return -1;
	}

	public static void beginTrain(String[] args) {
		int i;
		if (args.length == 0) {
			System.out.println("WORD VECTOR estimation toolkit v 0.1b\n\n");
			System.out.println("Options:\n");
			System.out.println("Parameters for training:\n");
			System.out.println("\t-train <file>\n");
			System.out.println("\t\tUse text data from <file> to train the model\n");
			System.out.println("\t-output <file>\n");
			System.out.println("\t\tUse <file> to save the resulting word vectors / word clusters\n");
			System.out.println("\t-size <int>\n");
			System.out.println("\t\tSet size of word vectors; default is 100\n");
			System.out.println("\t-window <int>\n");
			System.out.println("\t\tSet max skip length between words; default is 5\n");
			System.out.println("\t-sample <float>\n");
			System.out.println("\t\tSet threshold for occurrence of words. Those that appear with higher frequency");
			System.out.println(" in the training data will be randomly down-sampled; default is 0 (off), useful value is 1e-5\n");
			System.out.println("\t-hs <int>\n");
			System.out.println("\t\tUse Hierarchical Softmax; default is 1 (0 = not used)\n");
			System.out.println("\t-negative <int>\n");
			System.out.println("\t\tNumber of negative examples; default is 0, common values are 5 - 10 (0 = not used)\n");
			System.out.println("\t-threads <int>\n");
			System.out.println("\t\tUse <int> threads (default 1)\n");
			System.out.println("\t-min-count <int>\n");
			System.out.println("\t\tThis will discard words that appear less than <int> times; default is 5\n");
			System.out.println("\t-alpha <float>\n");
			System.out.println("\t\tSet the starting learning rate; default is 0.025\n");
			System.out.println("\t-classes <int>\n");
			System.out.println("\t\tOutput word classes rather than word vectors; default number of classes is 0 (vectors are written)\n");
			System.out.println("\t-debug <int>\n");
			System.out.println("\t\tSet the debug mode (default = 2 = more info during training)\n");
			System.out.println("\t-binary <int>\n");
			System.out.println("\t\tSave the resulting vectors in binary moded; default is 0 (off)\n");
			System.out.println("\t-save-vocab <file>\n");
			System.out.println("\t\tThe vocabulary will be saved to <file>\n");
			System.out.println("\t-read-vocab <file>\n");
			System.out.println("\t\tThe vocabulary will be read from <file>, not constructed from the training data\n");
			System.out.println("\t-cbow <int>\n");
			System.out.println("\t\tUse the continuous bag of words model; default is 0 (skip-gram model)\n");
			System.out.println("\nExamples:\n");
			System.out.println("./word2vec -train data.txt -output vec.txt -debug 2 -size 200 -window 5 -sample 1e-4 -negative 5 -hs 0 -binary 0 -cbow 1\n\n");
			return;
		}
		int num = args.length;
		if ((i = argPos("-size", num, args)) >= 0)
			layer1Size = Integer.valueOf(args[i + 1]);
		if ((i = argPos("-train", num, args)) >= 0)
			trainFile = args[i + 1];
		if ((i = argPos("-save-vocab", num, args)) >= 0)
			saveVocabFile = args[i + 1];
		if ((i = argPos("-read-vocab", num, args)) >= 0)
			readVocabFile = args[i + 1];
		if ((i = argPos("-debug", num, args)) >= 0)
			debug_mode = Integer.valueOf(args[i + 1]);
		if ((i = argPos("-binary", num, args)) >= 0)
			binary = Integer.valueOf(args[i + 1]);
		if ((i = argPos("-cbow", num, args)) >= 0)
			cbow = Integer.valueOf(args[i + 1]);
		if ((i = argPos("-alpha", num, args)) >= 0)
			alpha = Double.valueOf(args[i + 1]);
		if ((i = argPos("-output", num, args)) >= 0)
			outputFile = args[i + 1];
		if ((i = argPos("-window", num, args)) >= 0)
			window = Integer.valueOf(args[i + 1]);
		if ((i = argPos("-sample", num, args)) >= 0)
			sample = Double.valueOf(args[i + 1]);
		if ((i = argPos("-hs", num, args)) >= 0)
			hs = Integer.valueOf(args[i + 1]);
		if ((i = argPos("-negative", num, args)) >= 0)
			negative = Integer.valueOf(args[i + 1]);
		if ((i = argPos("-threads", num, args)) >= 0)
			num_threads = Integer.valueOf(args[i + 1]);
		if ((i = argPos("-min-count", num, args)) >= 0)
			min_count = Integer.valueOf(args[i + 1]);
		if ((i = argPos("-classes", num, args)) >= 0)
			classes = Integer.valueOf(args[i + 1]);
		trainModel();
	}

	public static void main(String[] args) {
		initWV();
		//-train resultbig.txt -output vectors.bin -cbow 0 -size 200 -window 5 -negative 0 -hs 1 -sample 1e-3 -threads 12 -binary 1
		beginTrain(args);
	}
}

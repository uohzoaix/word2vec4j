package com.lauty.w2v;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

public class Word2Phrases {

	private static final Integer MAX_STRING = 60;
	private static final Integer VOCAB_HASH_SIZE = 500000000;
	private static String trainFile;
	private static String outputFile;
	private static VocabWord[] vocabs;
	private static int[] vocabHash;
	private static int debug_mode = 2, min_count = 5, min_reduce = 1;
	private static int vocabMaxSize = 10000, vocabSize = 0;
	private static long trainWords = 0;
	private static float threshold = 100;

	public static class VocabWord {
		Integer cn = 0;
		String word = "";

		public Integer getCn() {
			return cn;
		}

		public void setCn(Integer cn) {
			this.cn = cn;
		}

		public String getWord() {
			return word;
		}

		public void setWord(String word) {
			this.word = word;
		}

		@Override
		public String toString() {
			return getWord() + "," + getCn();
		}
	}

	public static void initWP() {
		vocabs = new VocabWord[vocabMaxSize];
		for (int i = 0; i < vocabMaxSize; i++) {
			vocabs[i] = new VocabWord();
		}
		vocabHash = new int[VOCAB_HASH_SIZE];
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

	public static void beginTrain(String[] args) throws FileNotFoundException {
		int i;
		if (args.length == 0) {
			System.out.println("WORD2PHRASE tool v0.1a\n");
			System.out.println("Options:");
			System.out.println("Parameters for training:");
			System.out.println("\t-train <file>");
			System.out.println("\t\tUse text data from <file> to train the model");
			System.out.println("\t-output <file>");
			System.out.println("\t\tUse <file> to save the resulting word vectors / word clusters / phrases");
			System.out.println("\t-min-count <int>");
			System.out.println("\t\tThis will discard words that appear less than <int> times; default is 5");
			System.out.println("\t-threshold <float>");
			System.out.println("\t\t The <float> value represents threshold for forming the phrases (higher means less phrases); default 100");
			System.out.println("\t-debug <int>");
			System.out.println("\t\tSet the debug mode (default = 2 = more info during training)");
			System.out.println("\nExamples:\n");
			System.out.println("./word2phrase -train text.txt -output phrases.txt -threshold 100 -debug 2\n");
			return;
		}
		int num = args.length;
		if ((i = argPos("-train", num, args)) > 0)
			trainFile = args[i + 1];
		if ((i = argPos("-debug", num, args)) > 0)
			debug_mode = Integer.valueOf(args[i + 1]);
		if ((i = argPos("-output", num, args)) > 0)
			outputFile = args[i + 1];
		if ((i = argPos("-min-count", num, args)) > 0)
			min_count = Integer.valueOf(args[i + 1]);
		if ((i = argPos("-threshold", num, args)) > 0)
			threshold = Integer.valueOf(args[i + 1]);
		trainModel();
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
		if (length > MAX_STRING)
			length = MAX_STRING;
		word = word.substring(0, length);
		vocabs[vocabSize].setWord(word);
		vocabs[vocabSize].setCn(0);
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
		Comparator<VocabWord> comparator = new Comparator<Word2Phrases.VocabWord>() {
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

	public static void trainModel() throws FileNotFoundException {
		int pa = 0, pb = 0, pab = 0, oov, i, li = -1, cn = 0;

		String last_word = "", bigram_word = "";
		float score;
		System.out.println(String.format("Starting training using file %s", trainFile));
		learnVocabFromTrainFile();
		DataOutputStream dos = null;
		BufferedReader br = null;
		try {
			br = new BufferedReader(new InputStreamReader(new FileInputStream(trainFile)));
			dos = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(outputFile)));
			String tmp = null;
			while ((tmp = br.readLine()) != null) {
				String[] words = tmp.split(" ");
				int len = 0;
				for (String word : words) {
					last_word = len == 0 ? "" : word;
					len++;
					if (word.equals("</s>")) {
						dos.writeUTF("\n");
						continue;
					}
					cn++;
					if ((debug_mode > 1) && (cn % 100000 == 0)) {
						System.out.printf("Words written: %dK%c", cn / 1000, 13);
					}
					oov = 0;
					i = searchVocab(word);
					if (i == -1)
						oov = 1;
					else
						pb = vocabs[i].getCn();
					if (li == -1)
						oov = 1;
					li = i;
					bigram_word = last_word + "_" + word;
					i = searchVocab(bigram_word);
					if (i == -1)
						oov = 1;
					else
						pab = vocabs[i].getCn();
					if (pa < min_count)
						oov = 1;
					if (pb < min_count)
						oov = 1;
					if (oov > 0)
						score = 0;
					else
						score = (pab - min_count) / (float) pa / (float) pb * (float) trainWords;
					if (score > threshold) {
						dos.writeUTF("_" + word);
						pb = 0;
					} else
						dos.writeUTF(word);
					pa = pb;
				}
			}
		} catch (Exception e) {
		} finally {
			try {
				if (dos != null)
					dos.close();
				if (br != null)
					br.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	public static void learnVocabFromTrainFile() {
		String last_word = "", bigram_word = "";
		int a, i, start = 1;
		for (a = 0; a < VOCAB_HASH_SIZE; a++)
			vocabHash[a] = -1;
		BufferedReader br = null;
		try {
			br = new BufferedReader(new InputStreamReader(new FileInputStream(trainFile)));
			String tmp;
			while ((tmp = br.readLine()) != null) {
				String words[] = tmp.split(" ");
				trainWords += words.length;
				if (debug_mode > 0 && trainWords % 100000 == 0) {
					System.out.printf("%dK %d%c", trainWords / 1000, vocabSize, 13);
				}
				for (String word : words) {
					if (word.equals("</s>")) {
						start = 1;
						continue;
					} else {
						start = 0;
					}
					i = searchVocab(word);
					if (i == -1) {
						try {
							a = addWordToVocab(word);
							vocabs[a].setCn(1);
						} catch (Exception e) {
							e.printStackTrace();
						}
					} else
						vocabs[i].setCn(vocabs[i].getCn() + 1);
					if (start > 0) {
						continue;
					}
					bigram_word = last_word + "_" + word;
					last_word = word;
					i = searchVocab(bigram_word);
					if (i == -1) {
						a = addWordToVocab(bigram_word);
						vocabs[a].setCn(1);
					} else
						vocabs[i].setCn(vocabs[i].getCn() + 1);
					if (vocabSize > VOCAB_HASH_SIZE * 0.7)
						reduceVocab();
				}
			}
			sortVocab();
			if (debug_mode > 0) {
				System.out.println(String.format("Vocab size (unigrams + bigrams): %d\n", vocabSize));
				System.out.println(String.format("Words in train file: %d\n", trainWords));
			}
		} catch (Exception e) {
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

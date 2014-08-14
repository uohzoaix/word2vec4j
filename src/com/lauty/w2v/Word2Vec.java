package com.lauty.w2v;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PushbackReader;
import java.io.RandomAccessFile;
import java.util.Arrays;
import java.util.Comparator;

import com.lauty.w2v.util.UnReadRAF;

public class Word2Vec {

	private static final Integer MAX_STRING = 100;
	private static final Integer EXP_TABLE_SIZE = 1000;
	private static final Integer MAX_EXP = 6;
	private static final Integer MAX_SENTENCE_LENGTH = 1000;
	private static final Integer MAX_CODE_LENGTH = 40;
	private static final Integer VOCAB_HASH_SIZE = 30000000;

	public static class VocabWord {
		Integer cn;
		int[] point;
		char[] word, code;
		Integer codeLen;

		public Integer getCn() {
			return cn;
		}

		public void setCn(Integer cn) {
			this.cn = cn;
		}

		public int[] getPoint() {
			return point;
		}

		public void setPoint(int[] point) {
			this.point = point;
		}

		public char[] getWord() {
			return word;
		}

		public void setWord(char[] word) {
			this.word = word;
		}

		public char[] getCode() {
			return code;
		}

		public void setCode(char[] code) {
			this.code = code;
		}

		public Integer getCodeLen() {
			return codeLen;
		}

		public void setCodeLen(Integer codeLen) {
			this.codeLen = codeLen;
		}

	}

	VocabWord[] vocabs;

	int[] table;
	int[] vocabHash;
	private String trainFile = "";
	private char[] outputFile = new char[MAX_STRING];
	private String saveVocabFile = "";
	private String readVocabFile = "";
	int binary = 0, cbow = 0, debug_mode = 2, window = 5, min_count = 5, num_threads = 1, min_reduce = 1;
	int vocabMaxSize = 1000, vocabSize = 0, layer1Size = 100;
	long trainWords = 0, wordCountActual = 0, fileSize = 0, classes = 0;
	float alpha = 0.025f, startingAlpha, sample = 0;
	int hs = 1, negative = 0;
	double tableSize = 1e8;

	public void initUniGramTable() {
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

	// Reads a single word from a file, assuming space + tab + EOL to be word boundaries
	public void readWord(char[] word, UnReadRAF unraf) {
		//PushbackReader pushbackReader = new PushbackReader(reader);
		int a = 0, ch;
		try {
			while ((ch = unraf.read()) != -1) {
				if (ch == 13)
					continue;
				if (ch == ' ' || ch == '\t' || ch == '\n') {
					if (a > 0) {
						if (ch == '\n') {
							unraf.unread();
						}
						break;
					}
					if (ch == '\n') {
						word = "</s>".toCharArray();
						return;
					} else
						continue;
				}
				word[a] = (char) ch;
				a++;
				if (a >= MAX_STRING - 1)
					a--;
			}
			word[a] = 0;
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	// Returns hash value of a word
	public int getWordHash(char[] word) {
		int a, hash = 0;
		for (a = 0; a < word.length; a++)
			hash = hash * 257 + word[a];
		hash = hash % VOCAB_HASH_SIZE;
		return hash;
	}

	// Returns position of a word in the vocabulary; if the word is not found, returns -1
	public int searchVocab(char[] word) {
		int hash = getWordHash(word);
		while (true) {
			if (vocabHash[hash] == -1)
				return -1;
			if (new String(word).compareTo(new String(vocabs[vocabHash[hash]].getWord())) == 0) {
				return vocabHash[hash];
			}
			hash = (hash + 1) % VOCAB_HASH_SIZE;
		}
	}

	public int readWordIndex(UnReadRAF unraf) {
		char[] word = new char[MAX_STRING];
		readWord(word, unraf);
		try {
			if (unraf.read() == -1)
				return -1;
		} catch (IOException e) {
			e.printStackTrace();
		}
		return searchVocab(word);
	}

	// Adds a word to the vocabulary
	public int addWordToVocab(char[] word) {
		int hash, length = word.length + 1;
		if (length > MAX_STRING)
			length = MAX_STRING;
		vocabs[vocabSize].setWord(word);
		vocabs[vocabSize].setCn(0);
		vocabSize++;
		// Reallocate memory if needed
		if (vocabSize + 2 >= vocabMaxSize) {
			vocabMaxSize += 1000;
			vocabs = new VocabWord[vocabMaxSize];
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
	public void sortVocab() {
		int a, size;
		int hash;
		// Sort the vocabulary and keep </s> at the first position
		Comparator<VocabWord> comparator = new Comparator<Word2Vec.VocabWord>() {
			@Override
			public int compare(VocabWord o1, VocabWord o2) {
				return o2.getCn() - o1.getCn();
			}
		};
		Arrays.sort(vocabs, comparator);
		for (a = 0; a < VOCAB_HASH_SIZE; a++)
			vocabHash[a] = -1;
		size = vocabSize;
		trainWords = 0;
		for (a = 0; a < size; a++) {
			// Words occuring less than min_count times will be discarded from the vocab
			if (vocabs[a].getCn() < min_count) {
				vocabSize--;
				vocabs[vocabSize].setWord(null);
			} else {
				// Hash will be re-computed, as after the sorting it is not actual
				hash = getWordHash(vocabs[a].word);
				while (vocabHash[hash] != -1)
					hash = (hash + 1) % VOCAB_HASH_SIZE;
				vocabHash[hash] = a;
				trainWords += vocabs[a].cn;
			}
		}
		vocabs = new VocabWord[vocabSize + 1];
		// vocab = (struct vocab_word *)realloc(vocab, (vocab_size + 1) * sizeof(struct vocab_word));
		// Allocate memory for the binary tree construction
		for (a = 0; a < vocabSize; a++) {
			vocabs[a].setCode(new char[MAX_CODE_LENGTH]);
			vocabs[a].setPoint(new int[MAX_CODE_LENGTH]);
		}
	}

	// Reduces the vocabulary by removing infrequent tokens
	public void reduceVocab() {
		int a, b = 0;
		int hash;
		for (a = 0; a < vocabSize; a++)
			if (vocabs[a].cn > min_reduce) {
				vocabs[b].setCn(vocabs[a].getCn());
				vocabs[b].setWord(vocabs[a].getWord());
				b++;
			} else
				vocabs[a].setWord(null);
		vocabSize = b;
		for (a = 0; a < VOCAB_HASH_SIZE; a++)
			vocabHash[a] = -1;
		for (a = 0; a < vocabSize; a++) {
			// Hash will be re-computed, as it is not actual
			hash = getWordHash(vocabs[a].word);
			while (vocabHash[hash] != -1)
				hash = (hash + 1) % VOCAB_HASH_SIZE;
			vocabHash[hash] = a;
		}
		min_reduce++;
	}

	// Create binary Huffman tree using the word counts
	// Frequent words will have short uniqe binary codes
	public void createBinaryTree() {
		int a, b, i, min1i, min2i, pos1, pos2;
		int[] point = new int[MAX_CODE_LENGTH];
		char[] code = new char[MAX_CODE_LENGTH];
		int[] count = new int[vocabSize * 2 + 1];
		char[] binary = new char[vocabSize * 2 + 1];
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
			vocabs[a].getPoint()[0] = vocabSize - 2;
			for (b = 0; b < i; b++) {
				vocabs[a].getCode()[i - b - 1] = code[b];
				vocabs[a].getPoint()[i - b] = point[b] - vocabSize;
			}
		}
		count = null;
		binary = null;
		parentNode = null;
	}

	public void learnVocabFromTrainFile() {
		UnReadRAF unraf = null;
		try {
			char[] word = new char[MAX_STRING];
			int a, i;
			for (a = 0; a < VOCAB_HASH_SIZE; a++)
				vocabHash[a] = -1;
			unraf = new UnReadRAF(trainFile, "rb");
			vocabSize = 0;
			addWordToVocab("</s>".toCharArray());
			while (true) {
				readWord(word, unraf);
				if (unraf.read() == -1) {
					break;
				}
				trainWords++;
				if ((debug_mode > 1) && (trainWords % 100000 == 0)) {
					System.out.printf("%dK%c", trainWords / 1000, 13);
					System.out.println();
				}
				i = searchVocab(word);
				if (i == -1) {
					a = addWordToVocab(word);
					vocabs[a].setCn(1);
				} else
					vocabs[i].setCn(vocabs[i].getCn() + 1);
				if (vocabSize > VOCAB_HASH_SIZE * 0.7)
					reduceVocab();
			}
			sortVocab();
			if (debug_mode > 0) {
				System.out.printf("Vocab size: %d\n", vocabSize);
				System.out.printf("Words in train file: %d\n", trainWords);
			}
			fileSize = unraf.getFilePointer();
		} catch (Exception e) {
			if (unraf == null) {
				System.out.println("ERROR: training data file not found!\n");
				System.exit(1);
			}
		} finally {
			if (unraf != null) {
				try {
					unraf.close();
				} catch (Exception e2) {
				}
			}
		}
	}

	public void saveVocab() {
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

	public void readVocab() {
		UnReadRAF unraf = null;
		try {
			int a, i = 0;
			//char c;
			char[] word = new char[MAX_STRING];
			unraf = new UnReadRAF(readVocabFile, "rb");
			for (a = 0; a < VOCAB_HASH_SIZE; a++)
				vocabHash[a] = -1;
			vocabSize = 0;
			while (true) {
				readWord(word, unraf);
				if (unraf.read() == -1) {
					break;
				}
				a = addWordToVocab(word);
				// fscanf(fin, "%lld%c", &vocab[a].cn, &c);
				i++;
			}
			sortVocab();
			if (debug_mode > 0) {
				System.out.printf("Vocab size: %d\n", vocabSize);
				System.out.printf("Words in train file: %d\n", trainWords);
			}
			unraf = new UnReadRAF(trainFile, "rb");
			unraf.seek(unraf.length());
			fileSize = unraf.getFilePointer();
		} catch (Exception e) {
			e.printStackTrace();
			if (unraf == null) {
				System.exit(1);
			}
		} finally {
			if (unraf != null) {
				try {
					unraf.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}

	}

	public static void main(String[] args) {
		char[] word = new char[] { 'a', 'b', 'c' };
		System.out.println(String.format("%s %d\n", word, 11));
	}
}

package com.lauty.w2v;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.util.Arrays;

import com.lauty.w2v.Word2Vec.VocabWord;

public class Word2Phrases {

	private static final Integer MAX_STRING = 60;
	private static final Integer VOCAB_HASH_SIZE = 500000000;
	private static String trainFile;
	private static String outputFile;
	private static VocabWord[] vocabs;
	private static int[] vocabHash;
	private static int debug_mode = 2, min_count = 5, min_reduce = 1;
	private static int vocabMaxSize = 10000, vocab_size = 0;
	private static long train_words = 0;
	private static float threshold = 100;
	private static long next_random = 1;

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

	public static void beginTrain(String[] args) {
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

	public static void trainModel() {
		long pa = 0, pb = 0, pab = 0, oov, i, li = -1, cn = 0;
		
		  char word[MAX_STRING], last_word[MAX_STRING], bigram_word[MAX_STRING * 2];
		  float score;
		  System.out.println(String.format("Starting training using file %s", trainFile));
		  learnVocabFromTrainFile();
		  DataOutputStream dos = null;
		  BufferedReader br=null;
		  try {
			  br = new BufferedReader(new InputStreamReader(new FileInputStream(trainFile)));
			  dos = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(outputFile)));
		} catch (Exception e) {
		}
	}

	public static void learnVocabFromTrainFile() throws FileNotFoundException {
		String[] word = new String[MAX_STRING], last_word = new String[MAX_STRING], bigram_word = new String[MAX_STRING * 2];
		int a, i, start = 1;
		for (a = 0; a < VOCAB_HASH_SIZE; a++)
			vocabHash[a] = -1;
		BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(trainFile)));
	}
}

package com.lauty.w2v;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.FileInputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

public class Distance {

	public class WordEntry implements Comparable<WordEntry> {
		public String name;
		public double score;

		public WordEntry(String name, double score) {
			this.name = name;
			this.score = score;
		}

		@Override
		public String toString() {
			return this.name + "\t" + score;
		}

		@Override
		public int compareTo(WordEntry o) {
			if (this.score < o.score) {
				return 1;
			} else {
				return -1;
			}
		}

	}

	private HashMap<String, double[]> wordScores = new HashMap<String, double[]>();

	public static final Integer MAX_SIZE = 2000; // max length of strings
	public static final Integer N = 40; // number of closest words that will be shown
	public static final Integer MAX_W = 50; // max length of vocabulary entries

	String[] bestw = new String[N];
	char[][] st = new char[100][MAX_SIZE];
	double dist, len;
	double[] bestd = new double[N];
	double[] vec = new double[MAX_SIZE];
	int words, size, a, b, c, d, cn;
	int[] bi = new int[100];
	double[] M;
	String[] vocab;

	WordEntry[] entries;

	private DataInputStream dis;

	public Distance(String file, String test) throws Exception {
		if (file == null || "".equals(file)) {
			throw new Exception("fileName为空");
		}
		dis = new DataInputStream(new BufferedInputStream(new FileInputStream(file)));
		if (dis == null) {
			throw new Exception("Input file not found");
		}
		words = dis.readInt();
		size = dis.readInt();
		vocab = new String[words * MAX_W];
		M = new double[words * size];
		for (b = 0; b < words; b++) {
			String word = dis.readUTF();
			vocab[b * MAX_W] = word;
			if (word.equals("中")) {
				for (a = 0; a < size; a++) {
					double score = dis.readDouble();
					System.out.println(score);
					M[a + b * size] = score;
				}
			} else {
				for (a = 0; a < size; a++)
					M[a + b * size] = dis.readDouble();
			}
			len = 0;
			for (a = 0; a < size; a++)
				len += M[a + b * size] * M[a + b * size];
			len = Math.sqrt(len);
			for (a = 0; a < size; a++)
				M[a + b * size] /= len;
		}
		if (dis != null)
			dis.close();
	}

	public Distance(String file) throws Exception {
		if (file == null || "".equals(file)) {
			throw new Exception("fileName为空");
		}
		dis = new DataInputStream(new BufferedInputStream(new FileInputStream(file)));
		if (dis == null) {
			throw new Exception("Input file not found");
		}
		words = dis.readInt();
		size = dis.readInt();
		String word;
		double[] vectors = null;
		for (int i = 0; i < words; i++) {
			word = dis.readUTF();
			vectors = new double[size];
			len = 0;
			for (int j = 0; j < size; j++) {
				double score = dis.readDouble();
				len += score * score;
				vectors[j] = score;
			}
			len = Math.sqrt(len);

			for (int j = 0; j < size; j++) {
				vectors[j] /= len;
			}
			wordScores.put(word, vectors);
		}
		if (dis != null)
			dis.close();
	}

	public void distance1(String word) throws Exception {
		if (word.length() > MAX_SIZE)
			throw new Exception("word is to long,must < " + MAX_SIZE);
		char[] st1 = word.toCharArray();
		cn = 0;
		b = 0;
		c = 0;
		while (true) {
			st[cn][b] = st1[c];
			b++;
			c++;
			st[cn][b] = 0;
			if (c >= st1.length)
				break;
			if (st1[c] == ' ') {
				cn++;
				b = 0;
				c++;
			}
		}
		cn++;
		for (a = 0; a < cn; a++) {
			for (b = 0; b < words; b++) {
				if (vocab[b * MAX_W].equals(String.valueOf(st[a]).trim())) {
					break;
				}
			}
			if (b == words)
				b = -1;
			bi[a] = b;
			System.out.println(String.format("Word: %s  Position in vocabulary: %d", String.valueOf(st[a]), bi[a]));
			if (b == -1) {
				System.out.println("Out of dictionary word!");
				break;
			}
		}
		if (b == -1) {
			System.out.println("Out of dictionary word!");
		}
		System.out.println("                  Word       Cosine distance\n-----------------------------------------\n");
		for (b = 0; b < cn; b++) {
			if (bi[b] == -1)
				continue;
			for (a = 0; a < size; a++)
				vec[a] += M[a + bi[b] * size];
		}
		len = 0;
		for (a = 0; a < size; a++)
			len += vec[a] * vec[a];
		len = Math.sqrt(len);
		for (a = 0; a < size; a++)
			vec[a] /= len;
		for (a = 0; a < N; a++)
			bestd[a] = -1;
		for (c = 0; c < words; c++) {
			a = 0;
			for (b = 0; b < cn; b++)
				if (bi[b] == c)
					a = 1;
			if (a == 1)
				continue;
			dist = 0;
			for (a = 0; a < size; a++)
				dist += vec[a] * M[a + c * size];
			for (a = 0; a < N; a++) {
				if (dist > bestd[a]) {
					for (d = N - 1; d > a; d--) {
						bestd[d] = bestd[d - 1];
						bestw[d] = bestw[d - 1];
					}
					bestd[a] = dist;
					bestw[a] = vocab[c * MAX_W];
					break;
				}
			}
		}
		for (a = 0; a < N; a++)
			System.out.println(String.format("%50s\t\t%f", bestw[a], bestd[a]));
	}

	public Set<WordEntry> distance(String word) {
		double[] center = wordScores.get(word);
		if (center == null) {
			return Collections.emptySet();
		}
		int resultSize = wordScores.size() < N ? wordScores.size() : N;
		TreeSet<WordEntry> result = new TreeSet<WordEntry>();

		double min = Float.MIN_VALUE;
		for (Map.Entry<String, double[]> entry : wordScores.entrySet()) {
			if (entry.getKey().equals(word)) {
				continue;
			}
			double[] vector = entry.getValue();
			double dist = 0;
			for (int i = 0; i < vector.length; i++) {
				dist += center[i] * vector[i];
			}
			if (dist > min) {
				result.add(new WordEntry(entry.getKey(), dist));
				if (resultSize < result.size()) {
					result.pollLast();
				}
				min = result.last().score;
			}
		}
		result.pollFirst();
		return result;
	}

	public static void main(String[] args) throws Exception {
		Distance distance = new Distance("vectors.bin");
		//distance.distance("过年");
		System.out.println(distance.distance("中"));
	}
}

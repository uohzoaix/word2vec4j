package com.lauty.w2v;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;

public class Distance {

	public static final Integer MAX_SIZE = 2000; // max length of strings
	public static final Integer N = 40; // number of closest words that will be shown
	public static final Integer MAX_W = 50; // max length of vocabulary entries

	byte[] st1 = new byte[MAX_SIZE];
	byte[][] bestw = new byte[N][];
	byte[][] st = new byte[100][MAX_SIZE];
	double dist, len;
	double[] bestd = new double[N];
	double[] vec = new double[MAX_SIZE];
	int words, size, a, b, c, d, cn;
	int[] bi = new int[100];
	double[] M;
	String[] vocab;

	private DataInputStream dis;

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
		vocab = new String[words * MAX_W];

		for (a = 0; a < N; a++)
			bestw[a] = new byte[MAX_SIZE];
		M = new double[words * size];
		for (b = 0; b < words; b++) {
			dis.readUTF();
			vocab[b * MAX_W] = dis.readUTF();
			for (a = 0; a < size; a++)
				M[a + b * size] = dis.readDouble();
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

	public void distance() throws Exception {

	}
}

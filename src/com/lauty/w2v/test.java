package com.lauty.w2v;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import com.lauty.w2v.util.UnReadRAF;

public class test {

	public static class TestThread extends Thread {
		@Override
		public void run() {
			for (int i = 0; i < 10000; i++) {
				System.out.println(Thread.currentThread().getId() + "-" + i);
			}
		}
	}

	public static void main1(String[] args) {
		for (int i = 0; i < 5; i++) {
			TestThread tt = new test.TestThread();
			tt.start();
			try {
				tt.join();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
	}

	public static void main2(String[] args) throws IOException {
		UnReadRAF unraf = new UnReadRAF("test.txt", "r");
		byte[] word = new byte[100];
		while (true) {
			int len = readWord(word, unraf);
			System.out.println(new String(Arrays.copyOfRange(word, 0, len)));
			if (unraf.read() == -1) {
				break;
			}
		}
	}

	public static int readWord(byte[] word, UnReadRAF unraf) {
		Arrays.fill(word, 0, word.length, (byte) 0);
		int a = 0, ch;
		try {
			while ((ch = unraf.read()) != -1) {
				if (ch == 13)
					continue;
				if (ch == ' ' || ch == '\t' || ch == '\n') {
					if (a > 0) {
						if (ch == ' ') {
							unraf.unread();
						}
						break;
					}
					if (ch == '\n') {
						word = "</s>".getBytes();
						unraf.unread();
						return "</s>".getBytes().length;
					} else
						continue;
				}
				word[a] = (byte) ch;
				a++;
				if (a >= 100 - 1)
					a--;
			}
			//System.out.println(new String(word));
		} catch (Exception e) {
			e.printStackTrace();
		}
		return a;
	}

}

package com.lauty.w2v;

public class test {

	public static class TestThread extends Thread {
		@Override
		public void run() {
			for (int i = 0; i < 10000; i++) {
				System.out.println(Thread.currentThread().getId() + "-" + i);
			}
		}
	}

	public static void main(String[] args) {
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
}

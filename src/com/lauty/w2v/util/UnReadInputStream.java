package com.lauty.w2v.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * when you want to push the current char you read back to the stream you can use this stream instead
 * @author uohzoaix
 *
 */
public class UnReadInputStream extends InputStream {
	private InputStream is;
	private boolean isUnRead;
	private int lastValue;

	public UnReadInputStream(InputStream is) {
		this.is = is;
		isUnRead = false;
		lastValue = -1;
	}

	@Override
	public int read() throws IOException {
		if (isUnRead) {
			isUnRead = false;
			return lastValue;
		}
		return lastValue = is.read();
	}

	public void unread() {
		if (isUnRead)
			throw new IllegalStateException("already unread");
		isUnRead = true;
	}

	public static void main(String[] args) {
		UnReadInputStream in = null;
		try {
			in = new UnReadInputStream(new FileInputStream(new File("")));
			int c;
			while ((c = in.read()) != -1) {
				if (c == 'A') {
					in.unread();
				} else {
					System.out.println(c);
				}
			}
		} catch (Exception e) {
		} finally {
			if (in != null) {
				try {
					in.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
	}
}

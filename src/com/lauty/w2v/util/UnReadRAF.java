package com.lauty.w2v.util;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;

public class UnReadRAF extends RandomAccessFile {

	private boolean isUnRead;
	private int lastValue;

	public UnReadRAF(String name, String mode) throws FileNotFoundException {
		super(name, mode);
	}

	@Override
	public int read() throws IOException {
		if (isUnRead) {
			isUnRead = false;
			return lastValue;
		}
		return lastValue = super.read();
	}

	public void unread() {
		if (isUnRead)
			throw new IllegalStateException("already unread");
		isUnRead = true;
	}

}

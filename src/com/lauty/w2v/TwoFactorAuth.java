package com.lauty.w2v;

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.apache.commons.codec.binary.Base32;

public class TwoFactorAuth {

	private static final String HMAC_SHA1_ALGORITHM = "HmacSHA1";

	public static byte[] toBytes(Long value) {
		int mask = 0xFF;
		int[] shifts = new int[] { 56, 48, 40, 32, 24, 16, 8, 0 };
		byte[] result = new byte[shifts.length];
		for (int i = 0; i < shifts.length; i++) {
			result[i] = (byte) ((value >> shifts[i]) & mask);
		}
		return result;
	}

	public static int toUint32(byte[] bytes) {
		return ((int) (bytes[0]) << 24) + ((int) (bytes[1]) << 16) + ((int) (bytes[2]) << 8) + (int) (bytes[3]);
	}

	public static int oneTimePassword(byte[] key, byte[] value) throws NoSuchAlgorithmException, InvalidKeyException {
		// sign the value using HMAC-SHA1
		SecretKeySpec signingKey = new SecretKeySpec(key, HMAC_SHA1_ALGORITHM);
		Mac mac = Mac.getInstance(HMAC_SHA1_ALGORITHM);
		mac.init(signingKey);
		byte[] hash = mac.doFinal(value);
		// We're going to use a subset of the generated hash.
		// Using the last nibble (half-byte) to choose the index to start from.
		// This number is always appropriate as it's maximum decimal 15, the hash will
		// have the maximum index 19 (20 bytes of SHA1) and we need 4 bytes.
		int offset = hash[hash.length - 1] & 0x0F;

		// get a 32-bit (4-byte) chunk from the hash starting at offset
		byte[] hashParts = Arrays.copyOfRange(hash, offset, offset + 4);

		// ignore the most significant bit as per RFC 4226
		hashParts[0] = (byte) (hashParts[0] & 0x7F);

		int number = toUint32(hashParts);

		// size to 6 digits
		// one million is the first number with 7 digits so the remainder
		// of the division will always return < 7 digits
		int pwd = number % 1000000;

		return pwd;
	}

	public static void main(String[] args) {
		if (args.length < 1) {
			System.err.println("must specify key to use");
			System.exit(1);
		}

		String input = args[0];

		// decode the key from the first argument
		String inputNoSpaces = input.replace(" ", "");
		String inputNoSpacesUpper = inputNoSpaces.toUpperCase();
		Base32 base32 = new Base32();
		byte[] key = base32.decode(inputNoSpacesUpper);

		// generate a one-time password using the time at 30-second intervals
		long epochSeconds = System.currentTimeMillis() / 1000;
		int pwd = 0;
		try {
			pwd = oneTimePassword(key, toBytes(epochSeconds / 30));
		} catch (InvalidKeyException e) {
			e.printStackTrace();
		} catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
		}

		long secondsRemaining = 30 - (epochSeconds % 30);
		System.out.println(String.format("%06d (%d second(s) remaining)", pwd, secondsRemaining));
	}
}

package com.lauty.w2v;
import java.net.URLDecoder;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

public class DecodePrice {

	private static final Boolean IS_BIG_ENDIAN = false;

	private static final Integer VERSION_LENGTH = 1;
	private static final Integer BIDID_LENGTH = 16;
	private static final Integer ENCODEPRICE_LENGTH = 4;
	private static final Integer CRC_LENGTH = 4;
	private static final Integer KEY_LENGTH = 16;

	private static final Integer VERSION_OFFSITE = 0;
	private static final Integer BIDID_OFFSITE = VERSION_OFFSITE + VERSION_LENGTH;
	private static final Integer ENCODEPRICE_OFFSITE = BIDID_OFFSITE + BIDID_LENGTH;
	private static final Integer CRC_OFFSITE = ENCODEPRICE_OFFSITE + ENCODEPRICE_LENGTH;
	private static final Integer MAGICTIME_OFFSET = 7;

	private static final byte[] G_KEY = { (byte) 0xf7, (byte) 0xdb, (byte) 0xeb, 0x73, 0x5b, 0x7a, 0x07, (byte) 0xf1, (byte) 0xcf, (byte) 0xca, 0x79, (byte) 0xcc, 0x1d, (byte) 0xfe, 0x4f, (byte) 0xa4 };

	public static int swab32(int x) {
		return (x & 0x000000ff << 24) | (x & 0x000000ff << 8) | (x & 0x000000ff << 8) | (x & 0x000000ff >> 24);
	}

	public static byte[] MD5(byte[] bytes) {
		MessageDigest md = null;
		try {
			md = MessageDigest.getInstance("MD5");
		} catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
		}
		md.update(bytes);
		byte[] bs = md.digest();
		//		for (byte b : bs) {
		//			System.out.print(b + "|");
		//		}
		return bs;
	}

	// Step list for decoding original price
	// If this function returns true, real price will return to realPrice 
	// variable and bidding time will return to time variable
	// If this function returns false, it means format error or checksum
	// error, realPrice variable and time variable are invalid
	public static Integer decodePrice(byte[] src, Integer realPrice, String time) throws Exception {
		// Get version and check
		int v = src[0];
		if (v != 1) {
			throw new Exception("version:" + v + "!=1,error");
		}

		// Copy bidid+key
		byte[] buf = new byte[64];

		System.arraycopy(src, BIDID_OFFSITE, buf, 0, BIDID_LENGTH);
		System.arraycopy(G_KEY, 0, buf, BIDID_LENGTH, KEY_LENGTH);

		// ctxBuf=MD5(bidid+key)
		byte[] ctxBuf = new byte[16];
		ctxBuf = MD5(Arrays.copyOfRange(buf, 0, 32));
		// MD5(buf, 32, ctxBuf);

		// Get settle price
		byte[] p1 = new byte[4];
		byte[] p2 = Arrays.copyOfRange(src, ENCODEPRICE_OFFSITE, src.length);
		byte[] p3 = ctxBuf;
		for (int i = 0; i < 4; ++i) {
			p1[i] = (byte) (p2[i] ^ p3[i]);
		}
		int price = fromByteArray(p1);
		// Big endian needs reverse price by byte
		if (IS_BIG_ENDIAN) {
			realPrice = swab32(price);
		} else {
			realPrice = price;
		}

		// Calc crc and compare with src
		// If not match, it is illegal

		// copy(version+bidid+settlePrice+key)
		int vb = VERSION_LENGTH + BIDID_LENGTH;
		byte[] pbuf = buf;
		//System.arraycopy(buf, 0, pbuf, 0, buf.length);
		System.arraycopy(src, 0, pbuf, 0, vb);//copy version+bidid

		// Notice: here is price not realPrice
		// More important for big endian !

		System.arraycopy(p1, 0, pbuf, vb, 4);// copy settlePrice
		System.arraycopy(G_KEY, 0, pbuf, vb + 4, KEY_LENGTH);// copy key

		// MD5(version+bidid+settlePrice+key)
		ctxBuf = MD5(Arrays.copyOfRange(pbuf, 0, VERSION_LENGTH + BIDID_LENGTH + 4 + KEY_LENGTH));
		int i, j = 0;
		for (i = CRC_OFFSITE; i < CRC_OFFSITE + CRC_LENGTH;) {
			for (; j < CRC_LENGTH;) {
				if (ctxBuf[j] != src[i]) {
					throw new Exception("checksum error!");
				}
				j++;
				i++;
				break;
			}
		}
		//time = new String(Arrays.copyOfRange(src, MAGICTIME_OFFSET, src.length));
		return realPrice;
	}

	public static int fromByteArray(byte[] bytes) {
		return bytes[3] << 24 | (bytes[2] & 0xFF) << 16 | (bytes[1] & 0xFF) << 8 | (bytes[0] & 0xFF);
	}

	private final static char[] ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/".toCharArray();
	private static int[] toInt = new int[128];

	static {
		for (int i = 0; i < ALPHABET.length; i++) {
			toInt[ALPHABET[i]] = i;
		}
	}

	public static byte[] base64Decode(String s) {
		int delta = s.endsWith("==") ? 2 : s.endsWith("=") ? 1 : 0;
		byte[] buffer = new byte[s.length() * 3 / 4 - delta];
		int mask = 0xFF;
		int index = 0;
		for (int i = 0; i < s.length(); i += 4) {
			int c0 = toInt[s.charAt(i)];
			int c1 = toInt[s.charAt(i + 1)];
			buffer[index++] = (byte) (((c0 << 2) | (c1 >> 4)) & mask);
			if (index >= buffer.length) {
				return buffer;
			}
			int c2 = toInt[s.charAt(i + 2)];
			buffer[index++] = (byte) (((c1 << 4) | (c2 >> 2)) & mask);
			if (index >= buffer.length) {
				return buffer;
			}
			int c3 = toInt[s.charAt(i + 3)];
			buffer[index++] = (byte) (((c2 << 6) | c3) & mask);
		}
		return buffer;
	}

	public static void main(String[] args) throws Exception {
		byte src[] = "AQtlz3ImkFBRQ5MAAADFkK5AX2YJU4kNig%3D%3D".getBytes();
		String origstr = URLDecoder.decode(new String(src), "UTF-8");
		byte[] orig = base64Decode(origstr);
		int origLen = orig.length;
		// 25 bytes fixed length
		if (origLen != VERSION_LENGTH + BIDID_LENGTH + ENCODEPRICE_LENGTH + CRC_LENGTH) {
			throw new Exception("base64 decode error!");
		}
		Integer price = null;
		String time = null;
		System.out.println(decodePrice(orig, price, time));
		//if (decodePrice(orig, price, time)) {
		//struct tm tm;
		//localtime_r(&time, &tm);

		//	System.out.println("Decode price ok, price = " + price + ",Bid time is " + time);
		//}
	}
}

package com.lauty.w2v;

import java.net.URLDecoder;
import java.nio.ByteBuffer;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Calendar;

import com.lauty.w2v.util.TanxUtil;

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

	private static final DateFormat formatter = new SimpleDateFormat("yyyy-MM-dd hh:mm:ss");
	private static final Calendar calendar = Calendar.getInstance();

	private static final byte[] G_KEY = { (byte) 0xf7, (byte) 0xdb, (byte) 0xeb, 0x73, 0x5b, 0x7a, 0x07, (byte) 0xf1, (byte) 0xcf, (byte) 0xca, 0x79, (byte) 0xcc, 0x1d, (byte) 0xfe, 0x4f, (byte) 0xa4 };

	// Step list for decoding original price
	// If this function returns true, real price will return to realPrice 
	// variable and bidding time will return to time variable
	// If this function returns false, it means format error or checksum
	// error, realPrice variable and time variable are invalid
	public static PriceTime decodePrice(byte[] src, PriceTime pt) throws Exception {
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
		ctxBuf = TanxUtil.MD5(Arrays.copyOfRange(buf, 0, 32));
		// MD5(buf, 32, ctxBuf);

		// Get settle price
		byte[] p1 = new byte[4];
		byte[] p2 = Arrays.copyOfRange(src, ENCODEPRICE_OFFSITE, src.length);
		byte[] p3 = ctxBuf;
		for (int i = 0; i < 4; ++i) {
			p1[i] = (byte) (p2[i] ^ p3[i]);
		}
		int price = TanxUtil.fromByteArray(p1);
		// Big endian needs reverse price by byte
		if (IS_BIG_ENDIAN) {
			pt.setPrice(TanxUtil.swab32(price));
		} else {
			pt.setPrice(price);
		}

		// Calc crc and compare with src
		// If not match, it is illegal
		checkSum(buf, src, p1);
		long mills = ByteBuffer.wrap(Arrays.copyOfRange(src, MAGICTIME_OFFSET, src.length)).getInt();
		mills = mills * 1000;
		calendar.setTimeInMillis(mills);
		pt.setTime(formatter.format(calendar.getTime()));
		return pt;
	}

	public static void checkSum(byte[] buf, byte[] src, byte[] p1) throws Exception {
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
		byte[] ctxBuf = TanxUtil.MD5(Arrays.copyOfRange(pbuf, 0, VERSION_LENGTH + BIDID_LENGTH + 4 + KEY_LENGTH));
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
	}

	static class PriceTime {
		private Integer price;
		private String time;

		public PriceTime(Integer price, String time) {
			super();
			this.price = price;
			this.time = time;
		}

		public Integer getPrice() {
			return price;
		}

		public void setPrice(Integer price) {
			this.price = price;
		}

		public String getTime() {
			return time;
		}

		public void setTime(String time) {
			this.time = time;
		}

		@Override
		public String toString() {
			return "price:" + getPrice() + ",time:" + getTime();
		}

	}

	public static void main(String[] args) throws Exception {
		byte src[] = "AQtlz3ImkFBRQ5MAAADFkK5AX2YJU4kNig%3D%3D".getBytes();
		String origstr = URLDecoder.decode(new String(src), "UTF-8");
		byte[] orig = TanxUtil.base64Decode(origstr);
		int origLen = orig.length;
		// 25 bytes fixed length
		if (origLen != VERSION_LENGTH + BIDID_LENGTH + ENCODEPRICE_LENGTH + CRC_LENGTH) {
			throw new Exception("base64 decode error!");
		}
		Integer price = null;
		String time = null;
		System.out.println(decodePrice(orig, new PriceTime(price, time)));
	}
}

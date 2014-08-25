package com.lauty.w2v;

import java.util.Arrays;

import com.lauty.w2v.util.TanxUtil;

public class DecodeDeviceId {

	private static final Integer VERSION_LENGTH = 1;
	private static final Integer DEVICEID_LENGTH = 1;
	private static final Integer CRC_LENGTH = 4;
	private static final Integer KEY_LENGTH = 16;

	private static final Integer VERSION_OFFSITE = 0;
	private static final Integer LENGTH_OFFSITE = (VERSION_OFFSITE + VERSION_LENGTH);
	private static final Integer ENCODEDEVICEID_OFFSITE = (LENGTH_OFFSITE + DEVICEID_LENGTH);

	private static final byte[] G_KEY = { (byte) 0xf7, (byte) 0xdb, (byte) 0xeb, 0x73, 0x5b, 0x7a, 0x07, (byte) 0xf1, (byte) 0xcf, (byte) 0xca, 0x79, (byte) 0xcc, 0x1d, (byte) 0xfe, 0x4f, (byte) 0xa4 };

	// Step list for decoding original deviceId
	// If this function returns true, real deviceId will 
	// return to realDeviceId variable
	// If this function returns false, it means format error or checksum
	// error, realDeviceId variable is invalid
	public static byte[] decodeDeviceId(byte[] src) throws Exception {
		// Get version and check
		int v = src[0];
		if (v != 1) {
			throw new Exception("version:" + v + "!=1,error!");
		}

		//get deviceId length
		int len = src[VERSION_LENGTH];

		// Copy key
		byte[] buf = new byte[64];
		System.arraycopy(G_KEY, 0, buf, 0, KEY_LENGTH);

		// ctxBuf=MD5(key)
		byte[] ctxBuf = TanxUtil.MD5(Arrays.copyOfRange(buf, 0, KEY_LENGTH));

		// Get deviceId
		byte[] deviceId = new byte[64];
		byte[] pDeviceId = deviceId;
		byte[] pEncodeDeviceId = Arrays.copyOfRange(src, ENCODEDEVICEID_OFFSITE, src.length);
		byte[] pXor = new byte[len];
		System.arraycopy(ctxBuf, 0, pXor, 0, len > KEY_LENGTH ? KEY_LENGTH : len);
		byte[] pXorB = Arrays.copyOfRange(pXor, 0, KEY_LENGTH);
		byte pXorE = 0;
		int k = 0;
		for (int i = 0; i < len; ++i) {
			if (k >= pXorB.length) {
				k = 0;
			}
			if (pXor[i] == pXorE) {
				pXor[i] = pXorB[k];
			}
			k++;
			pDeviceId[i] = (byte) (pEncodeDeviceId[i] ^ pXor[i]);
		}

		// Copy decode deviceId
		byte[] realDeviceId = Arrays.copyOfRange(deviceId, 0, len);

		// Calc crc and compare with src
		// If not match, it is illegal
		checkSum(buf, deviceId, src, len);
		return realDeviceId;
	}

	public static void checkSum(byte[] buf, byte[] deviceId, byte[] src, int len) throws Exception {
		// copy(version+length+deviceId+key)
		int vd = VERSION_LENGTH + DEVICEID_LENGTH;
		byte[] pbuf = buf;
		System.arraycopy(src, 0, pbuf, 0, vd);//copy version+bidid

		// Notice: here is deviceId not realDeviceId
		// More important for big endian !
		System.arraycopy(deviceId, 0, pbuf, vd, len);// copy deviceId
		System.arraycopy(G_KEY, 0, pbuf, vd + len, KEY_LENGTH);// copy key

		// MD5(version+length+deviceId+key)
		byte[] ctxBuf = TanxUtil.MD5(Arrays.copyOfRange(pbuf, 0, VERSION_LENGTH + DEVICEID_LENGTH + len + KEY_LENGTH));
		int i, j = 0;
		for (i = ENCODEDEVICEID_OFFSITE + len; i < ENCODEDEVICEID_OFFSITE + len + CRC_LENGTH;) {
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

	public static void main(String[] args) throws Exception {
		// The sample string for encoded deviceId
		// result: 
		//    Decode deviceId ok, deviceId = 0007C145-FFF2-4119-9293-BFB26E8D27BB
		byte src[] = "AREDlCzw4IKwG4XE4rllPwW0c166xOg=".getBytes();
		byte[] orig = TanxUtil.base64Decode(new String(src));
		System.out.println(new String(decodeDeviceId(orig)));
	}
}

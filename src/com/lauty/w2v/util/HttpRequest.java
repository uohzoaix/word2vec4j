package com.lauty.w2v.util;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;

public class HttpRequest {
	public static String sendGet(String urlNameString) {
		BufferedReader br = null;
		try {
			URLConnection connection = new URL(urlNameString).openConnection();
			connection.setRequestProperty("Accept-Charset", "utf-8");
			br = new BufferedReader(new InputStreamReader(connection.getInputStream()));
			return br.readLine();
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			try {
				if (br != null) {
					br.close();
				}
			} catch (Exception e2) {
			}
		}
		return null;
	}

	public static String sendPost(String url, final String msg, UploadFile[] ufs) {
		HttpURLConnection connection = null;
		OutputStream output = null;
		BufferedReader br = null;
		try {
			connection = (HttpURLConnection) (new URL(url).openConnection());
			connection.setDoOutput(true);
			connection.setDoInput(true);
			connection.setUseCaches(false);
			connection.setRequestMethod("POST");
			connection.setRequestProperty("connection", "Keep-Alive");
			connection.setRequestProperty("user-agent", "Mozilla/4.0 (compatible; MSIE 6.0; Windows NT 5.1; SV1)");
			connection.setRequestProperty("Charsert", "UTF-8");
			if (ufs != null && ufs.length > 0) {
				String BOUNDARYSTR = System.currentTimeMillis() + "";
				String BOUNDARY = "--" + BOUNDARYSTR + "\r\n";
				connection.setRequestProperty("Content-type", "multipart/form-data;boundary=" + BOUNDARYSTR);
				connection.connect();
				output = new DataOutputStream(connection.getOutputStream());
				StringBuilder sb = new StringBuilder();
				String[] msgs = msg.split("&");
				for (String content : msgs) {
					String[] contents = content.split("=", 2);
					sb.append(BOUNDARY);
					sb.append("Content-Disposition:form-data;name=\"");
					sb.append(contents[0]);
					sb.append("\"\r\n\r\n");
					sb.append(contents[1]);
					sb.append("\r\n");
				}
				output.write(sb.toString().getBytes());
				for (UploadFile file : ufs) {
					sb.setLength(0);
					output.write(BOUNDARY.getBytes());
					sb.append("Content-Disposition:form-data;Content-Type:application/octet-stream;name=\"" + file.getFieldName() + "");
					sb.append("\";filename=\"");
					sb.append(file.getFileName() + "\"\r\n\r\n");
					output.write(sb.toString().getBytes());
					InputStream fis = file.getData();
					byte[] buffer = new byte[8192];
					int count = 0;
					while ((count = fis.read(buffer)) != -1) {
						output.write(buffer, 0, count);
					}
					output.write("\r\n\r\n".getBytes());
					fis.close();
				}
				output.write(("--" + BOUNDARYSTR + "--\r\n").getBytes());
				output.flush();
			} else {
				connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded;charset=" + "utf-8");
				output = connection.getOutputStream();
				output.write(msg.getBytes("utf-8"));
			}
			br = new BufferedReader(new InputStreamReader(connection.getInputStream()));
			return br.readLine();
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			try {
				if (br != null) {
					br.close();
				}
				if (output != null) {
					output.close();
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		return null;
	}
}
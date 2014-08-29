package com.lauty.w2v.util;

import java.io.InputStream;

public class UploadFile {

	private InputStream data;
	private String fieldName;
	private String fileName;
	private String contentType = "application/octet-stream";

	public UploadFile(InputStream data, String fieldName, String fileName) {
		super();
		this.data = data;
		this.fileName = fileName;
		this.fieldName = fieldName;
	}

	public InputStream getData() {
		return data;
	}

	public void setData(InputStream data) {
		this.data = data;
	}

	public String getFileName() {
		return fileName;
	}

	public void setFileName(String fileName) {
		this.fileName = fileName;
	}

	public String getContentType() {
		return contentType;
	}

	public void setContentType(String contentType) {
		this.contentType = contentType;
	}

	public String getFieldName() {
		return fieldName;
	}

	public void setFieldName(String fieldName) {
		this.fieldName = fieldName;
	}

}

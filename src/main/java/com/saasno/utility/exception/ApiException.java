package com.saasno.utility.exception;

public class ApiException extends RuntimeException {

	private static final long serialVersionUID = 6764731279268665471L;

	private final String code;

	public ApiException(String message) {
		super(message);
		this.code = "500";
	}

	public ApiException(String code, String message) {
		super(message);
		this.code = code;
	}

	public ApiException(String code, String message, Throwable cause) {
		super(message, cause);
		this.code = code;
	}

	public String getCode() {
		return code;
	}

}

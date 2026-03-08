package org.snomed.annotate.exception;

import org.springframework.http.HttpStatus;

public class ServiceException extends Exception {

	private final HttpStatus httpStatus;

	public ServiceException(HttpStatus httpStatus, String message) {
		super(message);
		this.httpStatus = httpStatus;
	}

	public ServiceException(HttpStatus httpStatus, String message, Throwable cause) {
		super(message, cause);
		this.httpStatus = httpStatus;
	}

	public HttpStatus getHttpStatus() {
		return httpStatus;
	}

}

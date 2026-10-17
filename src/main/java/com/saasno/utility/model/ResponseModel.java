package com.saasno.utility.model;

import java.util.Arrays;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_DEFAULT)
public class ResponseModel<E> {

	public static final String SUCCESS = "Success";

	public static final String FAILED = "Gagal";

	public static final String NOT_FOUND = "Tidak ditemukan";

	public static final String BAD_REQUEST = "Permintaan tidak valid";

	public static final String UNAUTHORIZED = "Tidak diizinkan";

	public static final String EXPIRED = "Kedaluwarsa";

	public static final String INTERNAL_SERVER_ERROR = "Terjadi kesalahan pada server";

	public static final String EXTERNAL_SERVER_ERROR = "Terjadi kesalahan pada layanan eksternal";

	public static final String SESSION_EXPIRED = "Sesi berakhir";

	public static final String SESSION_NOT_FOUND = "Sesi tidak ditemukan";

	public static final List<String> messages = Arrays.asList(
			SUCCESS, FAILED, NOT_FOUND, BAD_REQUEST, UNAUTHORIZED, EXPIRED, INTERNAL_SERVER_ERROR,
			EXTERNAL_SERVER_ERROR, SESSION_EXPIRED, SESSION_NOT_FOUND);

	private String message;

	private E data;

	public ResponseModel() {

	}

	public ResponseModel(String message) {
		this.message = message;
	}

	public ResponseModel(String message, E data) {
		this.message = message;
		this.data = data;
	}

	public ResponseModel<E> success() {
		return new ResponseModel<E>(SUCCESS);
	}

	public ResponseModel<E> success(E data) {
		return new ResponseModel<E>(SUCCESS, data);
	}

	public ResponseModel<E> failed() {
		return new ResponseModel<E>(FAILED);
	}

	public ResponseModel<E> failed(E data) {
		return new ResponseModel<E>(FAILED, data);
	}

	public ResponseModel<E> notFound() {
		return new ResponseModel<E>(NOT_FOUND);
	}

	public ResponseModel<E> notFound(E data) {
		return new ResponseModel<E>(NOT_FOUND, data);
	}

	public ResponseModel<E> badRequest() {
		return new ResponseModel<E>(BAD_REQUEST);
	}

	public ResponseModel<E> badRequest(E data) {
		return new ResponseModel<E>(BAD_REQUEST, data);
	}

	public ResponseModel<E> unauthorized() {
		return new ResponseModel<E>(UNAUTHORIZED);
	}

	public ResponseModel<E> unauthorized(E data) {
		return new ResponseModel<E>(UNAUTHORIZED, data);
	}

	public ResponseModel<E> expired() {
		return new ResponseModel<E>(EXPIRED);
	}

	public ResponseModel<E> expired(E data) {
		return new ResponseModel<E>(EXPIRED, data);
	}

	public ResponseModel<E> internalServerError() {
		return new ResponseModel<E>(INTERNAL_SERVER_ERROR);
	}

	public ResponseModel<E> internalServerError(E data) {
		return new ResponseModel<E>(INTERNAL_SERVER_ERROR, data);
	}

	public ResponseModel<E> externalServerError() {
		return new ResponseModel<E>(EXTERNAL_SERVER_ERROR);
	}

	public ResponseModel<E> externalServerError(E data) {
		return new ResponseModel<E>(EXTERNAL_SERVER_ERROR, data);
	}

	public ResponseModel<E> sessionExpired() {
		return new ResponseModel<E>(SESSION_EXPIRED);
	}

	public ResponseModel<E> sessionExpired(E data) {
		return new ResponseModel<E>(SESSION_EXPIRED, data);
	}

	public ResponseModel<E> sessionNotFound() {
		return new ResponseModel<E>(SESSION_NOT_FOUND);
	}

	public ResponseModel<E> sessionNotFound(E data) {
		return new ResponseModel<E>(SESSION_NOT_FOUND, data);
	}

	public String getMessage() {
		return message;
	}

	public void setMessage(String message) {
		this.message = message;
	}

	public E getData() {
		return data;
	}

	public void setData(E data) {
		this.data = data;
	}

	public static boolean isDefaultMessage(String message) {
		return messages.contains(message);
	}

}

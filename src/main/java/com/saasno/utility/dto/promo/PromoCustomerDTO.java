package com.saasno.utility.dto.promo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

// Customer sasaran promo. Saat create/update cukup kirim id; serial dan name diisi sistem.
@JsonIgnoreProperties(ignoreUnknown = true)
public class PromoCustomerDTO {

	private String id;
	private String serial;
	private String name;

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getSerial() {
		return serial;
	}

	public void setSerial(String serial) {
		this.serial = serial;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

}

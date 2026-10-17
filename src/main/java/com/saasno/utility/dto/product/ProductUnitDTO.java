package com.saasno.utility.dto.product;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

// Satu pilihan satuan produk. value = kode yang disimpan, label = teks yang tampil.
@JsonIgnoreProperties(ignoreUnknown = true)
public class ProductUnitDTO {

	private String value;
	private String label;

	public ProductUnitDTO() {
	}

	public ProductUnitDTO(String value, String label) {
		this.value = value;
		this.label = label;
	}

	public String getValue() {
		return value;
	}

	public void setValue(String value) {
		this.value = value;
	}

	public String getLabel() {
		return label;
	}

	public void setLabel(String label) {
		this.label = label;
	}

}

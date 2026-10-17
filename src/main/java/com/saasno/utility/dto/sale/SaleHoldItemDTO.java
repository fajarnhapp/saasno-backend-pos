package com.saasno.utility.dto.sale;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

// Barang di keranjang tertahan, apa adanya (quantity dalam satuan yang dipilih kasir, unit kosong = satuan dasar).
// productSerial dan productName hanya diisi saat membaca.
@JsonIgnoreProperties(ignoreUnknown = true)
public class SaleHoldItemDTO {

	private String productId;
	private String productSerial;
	private String productName;
	private BigDecimal quantity;
	private String unit;
	private BigDecimal discount;

	public String getProductId() {
		return productId;
	}

	public void setProductId(String productId) {
		this.productId = productId;
	}

	public String getProductSerial() {
		return productSerial;
	}

	public void setProductSerial(String productSerial) {
		this.productSerial = productSerial;
	}

	public String getProductName() {
		return productName;
	}

	public void setProductName(String productName) {
		this.productName = productName;
	}

	public BigDecimal getQuantity() {
		return quantity;
	}

	public void setQuantity(BigDecimal quantity) {
		this.quantity = quantity;
	}

	public String getUnit() {
		return unit;
	}

	public void setUnit(String unit) {
		this.unit = unit;
	}

	public BigDecimal getDiscount() {
		return discount;
	}

	public void setDiscount(BigDecimal discount) {
		this.discount = discount;
	}

}

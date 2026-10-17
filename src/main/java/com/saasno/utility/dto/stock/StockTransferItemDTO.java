package com.saasno.utility.dto.stock;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

// Permintaan: unit opsional (satuan konversi produk). Respons: quantity selalu dalam satuan dasar (unit).
@JsonIgnoreProperties(ignoreUnknown = true)
public class StockTransferItemDTO {

	private String productId;
	private String productSerial;
	private String productName;
	private BigDecimal quantity;
	private String unit;

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

}

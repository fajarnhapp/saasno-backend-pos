package com.saasno.utility.dto.stock;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

// Permintaan memakai productId, quantity (hasil hitung), dan unit (satuan konversi, opsional). Respons: semua jumlah
// dalam satuan dasar (unit); difference = countedQuantity - systemQuantity.
@JsonIgnoreProperties(ignoreUnknown = true)
public class StockOpnameItemDTO {

	private String productId;
	private String productSerial;
	private String productName;
	private BigDecimal quantity;
	private String unit;
	private BigDecimal systemQuantity;
	private BigDecimal countedQuantity;
	private BigDecimal difference;

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

	public BigDecimal getSystemQuantity() {
		return systemQuantity;
	}

	public void setSystemQuantity(BigDecimal systemQuantity) {
		this.systemQuantity = systemQuantity;
	}

	public BigDecimal getCountedQuantity() {
		return countedQuantity;
	}

	public void setCountedQuantity(BigDecimal countedQuantity) {
		this.countedQuantity = countedQuantity;
	}

	public BigDecimal getDifference() {
		return difference;
	}

	public void setDifference(BigDecimal difference) {
		this.difference = difference;
	}

}

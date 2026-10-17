package com.saasno.utility.dto.stock;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

// Saldo stok satu produk di satu cabang. quantity dalam satuan dasar produk (unit).
@JsonIgnoreProperties(ignoreUnknown = true)
public class StockDTO {

	private String branchId;
	private String branchName;
	private String productId;
	private String productSerial;
	private String productName;
	private String unit;
	private BigDecimal quantity;
	private LocalDateTime modificated;

	public String getBranchId() {
		return branchId;
	}

	public void setBranchId(String branchId) {
		this.branchId = branchId;
	}

	public String getBranchName() {
		return branchName;
	}

	public void setBranchName(String branchName) {
		this.branchName = branchName;
	}

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

	public String getUnit() {
		return unit;
	}

	public void setUnit(String unit) {
		this.unit = unit;
	}

	public BigDecimal getQuantity() {
		return quantity;
	}

	public void setQuantity(BigDecimal quantity) {
		this.quantity = quantity;
	}

	public LocalDateTime getModificated() {
		return modificated;
	}

	public void setModificated(LocalDateTime modificated) {
		this.modificated = modificated;
	}

}

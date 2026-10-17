package com.saasno.utility.dto.stock;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

// Permintaan stok masuk atau koreksi. unit opsional (satuan konversi produk); kosong = satuan dasar.
// Untuk koreksi, quantity adalah jumlah hasil hitung (bukan selisih).
@JsonIgnoreProperties(ignoreUnknown = true)
public class StockChangeDTO {

	private String branchId;
	private String productId;
	private BigDecimal quantity;
	private String unit;
	private String note;

	public String getBranchId() {
		return branchId;
	}

	public void setBranchId(String branchId) {
		this.branchId = branchId;
	}

	public String getProductId() {
		return productId;
	}

	public void setProductId(String productId) {
		this.productId = productId;
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

	public String getNote() {
		return note;
	}

	public void setNote(String note) {
		this.note = note;
	}

}

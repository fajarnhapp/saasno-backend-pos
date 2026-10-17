package com.saasno.utility.dto.sale;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

// Permintaan memakai saleItemId, quantity (satuan dasar), dan restock (kosong = true; false untuk barang rusak:
// uang dikembalikan tetapi stok tidak bertambah); sisanya diisi sistem.
@JsonIgnoreProperties(ignoreUnknown = true)
public class SaleReturnItemDTO {

	private String saleItemId;
	private String productId;
	private String productSerial;
	private String productName;
	private String unit;
	private BigDecimal quantity;
	private BigDecimal refund;
	private Boolean restock;

	public String getSaleItemId() {
		return saleItemId;
	}

	public void setSaleItemId(String saleItemId) {
		this.saleItemId = saleItemId;
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

	public BigDecimal getRefund() {
		return refund;
	}

	public void setRefund(BigDecimal refund) {
		this.refund = refund;
	}

	public Boolean getRestock() {
		return restock;
	}

	public void setRestock(Boolean restock) {
		this.restock = restock;
	}

}

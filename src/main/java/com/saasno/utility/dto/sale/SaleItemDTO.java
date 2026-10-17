package com.saasno.utility.dto.sale;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

// Baris transaksi (quantity dalam satuan dasar). netTotal = jumlah akhir baris setelah promo, diskon, dan pajak.
@JsonIgnoreProperties(ignoreUnknown = true)
public class SaleItemDTO {

	private String id;
	private String productId;
	private String productSerial;
	private String productName;
	private String unit;
	private BigDecimal quantity;
	private BigDecimal unitPrice;
	private String priceSource;
	private BigDecimal tierMinQuantity;
	private BigDecimal discount;
	private BigDecimal lineTotal;
	private BigDecimal netTotal;
	private Boolean free;
	private BigDecimal returnedQuantity;
	private BigDecimal returnedAmount;

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
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

	public BigDecimal getUnitPrice() {
		return unitPrice;
	}

	public void setUnitPrice(BigDecimal unitPrice) {
		this.unitPrice = unitPrice;
	}

	public String getPriceSource() {
		return priceSource;
	}

	public void setPriceSource(String priceSource) {
		this.priceSource = priceSource;
	}

	public BigDecimal getTierMinQuantity() {
		return tierMinQuantity;
	}

	public void setTierMinQuantity(BigDecimal tierMinQuantity) {
		this.tierMinQuantity = tierMinQuantity;
	}

	public BigDecimal getDiscount() {
		return discount;
	}

	public void setDiscount(BigDecimal discount) {
		this.discount = discount;
	}

	public BigDecimal getLineTotal() {
		return lineTotal;
	}

	public void setLineTotal(BigDecimal lineTotal) {
		this.lineTotal = lineTotal;
	}

	public BigDecimal getNetTotal() {
		return netTotal;
	}

	public void setNetTotal(BigDecimal netTotal) {
		this.netTotal = netTotal;
	}

	public Boolean getFree() {
		return free;
	}

	public void setFree(Boolean free) {
		this.free = free;
	}

	public BigDecimal getReturnedQuantity() {
		return returnedQuantity;
	}

	public void setReturnedQuantity(BigDecimal returnedQuantity) {
		this.returnedQuantity = returnedQuantity;
	}

	public BigDecimal getReturnedAmount() {
		return returnedAmount;
	}

	public void setReturnedAmount(BigDecimal returnedAmount) {
		this.returnedAmount = returnedAmount;
	}

}

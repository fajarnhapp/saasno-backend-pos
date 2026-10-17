package com.saasno.utility.dto.product;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

// Hasil perhitungan harga akhir. priceSource: BASE | CUSTOMER_TYPE | CUSTOMER. Harga bertingkat dipakai
// hanya kalau lebih murah dari harga dasar/khusus (tierMinQuantity terisi).
@JsonIgnoreProperties(ignoreUnknown = true)
public class ProductPriceResultDTO {

	private String productId;
	private String customerId;
	private BigDecimal quantity;
	private BigDecimal basePrice;
	private String priceSource;
	private BigDecimal specialPrice;
	private BigDecimal tierMinQuantity;
	private BigDecimal unitPrice;
	private BigDecimal total;

	public String getProductId() {
		return productId;
	}

	public void setProductId(String productId) {
		this.productId = productId;
	}

	public String getCustomerId() {
		return customerId;
	}

	public void setCustomerId(String customerId) {
		this.customerId = customerId;
	}

	public BigDecimal getQuantity() {
		return quantity;
	}

	public void setQuantity(BigDecimal quantity) {
		this.quantity = quantity;
	}

	public BigDecimal getBasePrice() {
		return basePrice;
	}

	public void setBasePrice(BigDecimal basePrice) {
		this.basePrice = basePrice;
	}

	public String getPriceSource() {
		return priceSource;
	}

	public void setPriceSource(String priceSource) {
		this.priceSource = priceSource;
	}

	public BigDecimal getSpecialPrice() {
		return specialPrice;
	}

	public void setSpecialPrice(BigDecimal specialPrice) {
		this.specialPrice = specialPrice;
	}

	public BigDecimal getTierMinQuantity() {
		return tierMinQuantity;
	}

	public void setTierMinQuantity(BigDecimal tierMinQuantity) {
		this.tierMinQuantity = tierMinQuantity;
	}

	public BigDecimal getUnitPrice() {
		return unitPrice;
	}

	public void setUnitPrice(BigDecimal unitPrice) {
		this.unitPrice = unitPrice;
	}

	public BigDecimal getTotal() {
		return total;
	}

	public void setTotal(BigDecimal total) {
		this.total = total;
	}

}

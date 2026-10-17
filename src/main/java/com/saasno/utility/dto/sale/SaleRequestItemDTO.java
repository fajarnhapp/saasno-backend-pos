package com.saasno.utility.dto.sale;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

// Satu barang di keranjang. unit opsional (satuan konversi produk); kosong = satuan dasar.
// discount = diskon manual untuk barang ini (rupiah utuh).
@JsonIgnoreProperties(ignoreUnknown = true)
public class SaleRequestItemDTO {

	private String productId;
	private BigDecimal quantity;
	private String unit;
	private BigDecimal discount;

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

	public BigDecimal getDiscount() {
		return discount;
	}

	public void setDiscount(BigDecimal discount) {
		this.discount = discount;
	}

}

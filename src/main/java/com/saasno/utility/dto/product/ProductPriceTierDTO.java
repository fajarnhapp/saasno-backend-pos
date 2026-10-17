package com.saasno.utility.dto.product;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

// Pembelian minimal {minQuantity} (dalam satuan produk) dikenai {price} per satuan.
@JsonIgnoreProperties(ignoreUnknown = true)
public class ProductPriceTierDTO {

	private BigDecimal minQuantity;
	private BigDecimal price;

	public BigDecimal getMinQuantity() {
		return minQuantity;
	}

	public void setMinQuantity(BigDecimal minQuantity) {
		this.minQuantity = minQuantity;
	}

	public BigDecimal getPrice() {
		return price;
	}

	public void setPrice(BigDecimal price) {
		this.price = price;
	}

}

package com.saasno.utility.dto.product;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

// Harga khusus untuk satu sasaran. target TYPE: isi customerType (INDIVIDU/BISNIS); target CUSTOMER:
// isi customerId. customerName diisi sistem (read-only).
@JsonIgnoreProperties(ignoreUnknown = true)
public class ProductCustomerPriceDTO {

	private String target;
	private String customerType;
	private String customerId;
	private String customerName;
	private BigDecimal price;

	public String getTarget() {
		return target;
	}

	public void setTarget(String target) {
		this.target = target;
	}

	public String getCustomerType() {
		return customerType;
	}

	public void setCustomerType(String customerType) {
		this.customerType = customerType;
	}

	public String getCustomerId() {
		return customerId;
	}

	public void setCustomerId(String customerId) {
		this.customerId = customerId;
	}

	public String getCustomerName() {
		return customerName;
	}

	public void setCustomerName(String customerName) {
		this.customerName = customerName;
	}

	public BigDecimal getPrice() {
		return price;
	}

	public void setPrice(BigDecimal price) {
		this.price = price;
	}

}

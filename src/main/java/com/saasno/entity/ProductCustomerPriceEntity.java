package com.saasno.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.saasno.utility.generator.ID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

// Harga khusus produk untuk satu sasaran: tipe customer (target TYPE) atau customer tertentu
// (target CUSTOMER). Menggantikan harga dasar produk bagi sasaran tersebut.
@Entity
@Table(schema = "public", name = "product_customer_price")
public class ProductCustomerPriceEntity {

	@Id
	@Column(length = 64, nullable = false, updatable = false)
	private String id;

	@Column(name = "product_id", length = 64, nullable = false, updatable = false)
	private String productId;

	// TYPE | CUSTOMER
	@Column(length = 16, nullable = false)
	private String target;

	// Terisi bila target TYPE: INDIVIDU | BISNIS
	@Column(name = "customer_type", length = 16)
	private String customerType;

	// Terisi bila target CUSTOMER
	@Column(name = "customer_id", length = 64)
	private String customerId;

	@Column(precision = 19, scale = 2, nullable = false)
	private BigDecimal price;

	@Column(nullable = false)
	private LocalDateTime modificated;

	@Column(nullable = false, updatable = false)
	private LocalDateTime registered;

	public ProductCustomerPriceEntity() {
		this.id = ID.uuid();
	}

	@PrePersist
	public void beforeInsert() {
		this.registered = LocalDateTime.now();
		this.modificated = LocalDateTime.now();
	}

	@PreUpdate
	public void beforeUpdate() {
		this.modificated = LocalDateTime.now();
	}

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

	public BigDecimal getPrice() {
		return price;
	}

	public void setPrice(BigDecimal price) {
		this.price = price;
	}

	public LocalDateTime getModificated() {
		return modificated;
	}

	public void setModificated(LocalDateTime modificated) {
		this.modificated = modificated;
	}

	public LocalDateTime getRegistered() {
		return registered;
	}

	public void setRegistered(LocalDateTime registered) {
		this.registered = registered;
	}

}

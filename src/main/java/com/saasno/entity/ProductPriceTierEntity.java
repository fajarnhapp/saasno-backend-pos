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

// Harga bertingkat: pembelian minimal {minQuantity} (dalam satuan produk) dikenai {price} per
// satuan. Di bawah tingkat terkecil berlaku ProductEntity.price.
@Entity
@Table(schema = "public", name = "product_price_tier")
public class ProductPriceTierEntity {

	@Id
	@Column(length = 64, nullable = false, updatable = false)
	private String id;

	@Column(name = "product_id", length = 64, nullable = false, updatable = false)
	private String productId;

	@Column(name = "min_quantity", precision = 19, scale = 6, nullable = false)
	private BigDecimal minQuantity;

	@Column(precision = 19, scale = 2, nullable = false)
	private BigDecimal price;

	@Column(nullable = false)
	private LocalDateTime modificated;

	@Column(nullable = false, updatable = false)
	private LocalDateTime registered;

	public ProductPriceTierEntity() {
		this.id = ID.uuid();
	}

	public ProductPriceTierEntity(String productId, BigDecimal minQuantity, BigDecimal price) {
		this.id = ID.uuid();
		this.productId = productId;
		this.minQuantity = minQuantity;
		this.price = price;
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

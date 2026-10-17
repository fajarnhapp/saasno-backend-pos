package com.saasno.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

// Saldo stok per cabang dan produk, dalam satuan dasar produk. Hanya dibaca lewat JPA; perubahan saldo
// dilakukan StockRepository dengan SQL atomik (lihat StockRepository) supaya tidak pernah minus.
@Entity
@Table(schema = "public", name = "stock_balance")
@IdClass(StockBalanceId.class)
public class StockBalanceEntity {

	@Id
	@Column(name = "branch_id", length = 64, nullable = false, updatable = false)
	private String branchId;

	@Id
	@Column(name = "product_id", length = 64, nullable = false, updatable = false)
	private String productId;

	@Column(precision = 19, scale = 6, nullable = false)
	private BigDecimal quantity;

	@Column(nullable = false)
	private LocalDateTime modificated;

	// Hanya untuk filter/urutan (mis. product.name); tidak pernah ditulis dari sini.
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "product_id", insertable = false, updatable = false)
	private ProductEntity product;

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

	public LocalDateTime getModificated() {
		return modificated;
	}

	public void setModificated(LocalDateTime modificated) {
		this.modificated = modificated;
	}

	public ProductEntity getProduct() {
		return product;
	}

	public void setProduct(ProductEntity product) {
		this.product = product;
	}

}

package com.saasno.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.saasno.utility.generator.ID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

// Satu baris per perubahan saldo; append-only (dijaga trigger di database). quantity = selisih bertanda.
@Entity
@Table(schema = "public", name = "stock_movement")
public class StockMovementEntity {

	public static final String RECEIVE = "RECEIVE";
	public static final String ADJUST = "ADJUST";
	public static final String TRANSFER_OUT = "TRANSFER_OUT";
	public static final String TRANSFER_IN = "TRANSFER_IN";
	public static final String SALE = "SALE";
	public static final String RETURN = "RETURN";
	// Pemakaian stok sendiri oleh toko (bukan penjualan); nilainya dicatat sebagai biaya operasional.
	public static final String USE = "USE";

	@Id
	@Column(length = 64, nullable = false, updatable = false)
	private String id;

	@Column(name = "branch_id", length = 64, nullable = false, updatable = false)
	private String branchId;

	@Column(name = "product_id", length = 64, nullable = false, updatable = false)
	private String productId;

	@Column(length = 24, nullable = false, updatable = false)
	private String type;

	@Column(precision = 19, scale = 6, nullable = false, updatable = false)
	private BigDecimal quantity;

	@Column(name = "balance_after", precision = 19, scale = 6, nullable = false, updatable = false)
	private BigDecimal balanceAfter;

	@Column(name = "unit_cost", precision = 19, scale = 2, updatable = false)
	private BigDecimal unitCost;

	@Column(name = "reference_type", length = 24, updatable = false)
	private String referenceType;

	@Column(name = "reference_id", length = 64, updatable = false)
	private String referenceId;

	@Column(length = 500, updatable = false)
	private String note;

	@Column(name = "created_by", length = 64, nullable = false, updatable = false)
	private String createdBy;

	@Column(nullable = false, updatable = false)
	private LocalDateTime registered;

	// Hanya untuk filter/urutan (mis. product.name); tidak pernah ditulis dari sini.
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "product_id", insertable = false, updatable = false)
	private ProductEntity product;

	public StockMovementEntity() {
		this.id = ID.uuid();
	}

	@PrePersist
	public void beforeInsert() {
		// Boleh sudah diisi pemanggil (transaksi offline memakai waktu di kasir); mutasi tidak bisa diubah setelah tersimpan.
		if (this.registered == null) {
			this.registered = LocalDateTime.now();
		}
	}

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

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

	public String getType() {
		return type;
	}

	public void setType(String type) {
		this.type = type;
	}

	public BigDecimal getQuantity() {
		return quantity;
	}

	public void setQuantity(BigDecimal quantity) {
		this.quantity = quantity;
	}

	public BigDecimal getBalanceAfter() {
		return balanceAfter;
	}

	public void setBalanceAfter(BigDecimal balanceAfter) {
		this.balanceAfter = balanceAfter;
	}

	public String getReferenceType() {
		return referenceType;
	}

	public void setReferenceType(String referenceType) {
		this.referenceType = referenceType;
	}

	public String getReferenceId() {
		return referenceId;
	}

	public void setReferenceId(String referenceId) {
		this.referenceId = referenceId;
	}

	public String getNote() {
		return note;
	}

	public void setNote(String note) {
		this.note = note;
	}

	public String getCreatedBy() {
		return createdBy;
	}

	public void setCreatedBy(String createdBy) {
		this.createdBy = createdBy;
	}

	public LocalDateTime getRegistered() {
		return registered;
	}

	public void setRegistered(LocalDateTime registered) {
		this.registered = registered;
	}

	public ProductEntity getProduct() {
		return product;
	}

	public void setProduct(ProductEntity product) {
		this.product = product;
	}

	public BigDecimal getUnitCost() {
		return unitCost;
	}

	public void setUnitCost(BigDecimal unitCost) {
		this.unitCost = unitCost;
	}

}

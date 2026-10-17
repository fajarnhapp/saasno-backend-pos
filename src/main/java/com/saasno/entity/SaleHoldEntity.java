package com.saasno.entity;

import java.time.LocalDateTime;

import com.saasno.utility.generator.ID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

// Keranjang yang ditahan (mis. customer lupa dompet). Bukan catatan keuangan: tidak ada nomor struk, stok tidak
// dikunci, dan harga dihitung ulang saat dilanjutkan. HELD -> USED (dibayar) | DISCARDED (dibuang, alasan wajib).
@Entity
@Table(schema = "public", name = "sale_hold")
public class SaleHoldEntity {

	public static final String HELD = "HELD";
	public static final String USED = "USED";
	public static final String DISCARDED = "DISCARDED";

	@Id
	@Column(name = "id", length = 64, nullable = false, updatable = false)
	private String id;

	@Column(name = "serial", length = 40, nullable = false, updatable = false, unique = true)
	private String serial;

	@Column(name = "branch_id", length = 64, nullable = false, updatable = false)
	private String branchId;

	@Column(name = "label", length = 200)
	private String label;

	@Column(name = "cart", nullable = false, columnDefinition = "TEXT")
	private String cart;

	@Column(name = "status", length = 12, nullable = false)
	private String status;

	@Column(name = "created_by", length = 64, nullable = false, updatable = false)
	private String createdBy;

	@Column(name = "registered", nullable = false, updatable = false)
	private LocalDateTime registered;

	@Column(name = "modificated", nullable = false)
	private LocalDateTime modificated;

	@Column(name = "sale_id", length = 64)
	private String saleId;

	@Column(name = "used_by", length = 64)
	private String usedBy;

	@Column(name = "used_at")
	private LocalDateTime usedAt;

	@Column(name = "discarded_by", length = 64)
	private String discardedBy;

	@Column(name = "discarded_at")
	private LocalDateTime discardedAt;

	@Column(name = "discard_reason", length = 500)
	private String discardReason;

	public SaleHoldEntity() {
		this.id = ID.uuid();
		this.status = HELD;
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

	public String getSerial() {
		return serial;
	}

	public void setSerial(String serial) {
		this.serial = serial;
	}

	public String getBranchId() {
		return branchId;
	}

	public void setBranchId(String branchId) {
		this.branchId = branchId;
	}

	public String getLabel() {
		return label;
	}

	public void setLabel(String label) {
		this.label = label;
	}

	public String getCart() {
		return cart;
	}

	public void setCart(String cart) {
		this.cart = cart;
	}

	public String getStatus() {
		return status;
	}

	public void setStatus(String status) {
		this.status = status;
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

	public LocalDateTime getModificated() {
		return modificated;
	}

	public void setModificated(LocalDateTime modificated) {
		this.modificated = modificated;
	}

	public String getSaleId() {
		return saleId;
	}

	public void setSaleId(String saleId) {
		this.saleId = saleId;
	}

	public String getUsedBy() {
		return usedBy;
	}

	public void setUsedBy(String usedBy) {
		this.usedBy = usedBy;
	}

	public LocalDateTime getUsedAt() {
		return usedAt;
	}

	public void setUsedAt(LocalDateTime usedAt) {
		this.usedAt = usedAt;
	}

	public String getDiscardedBy() {
		return discardedBy;
	}

	public void setDiscardedBy(String discardedBy) {
		this.discardedBy = discardedBy;
	}

	public LocalDateTime getDiscardedAt() {
		return discardedAt;
	}

	public void setDiscardedAt(LocalDateTime discardedAt) {
		this.discardedAt = discardedAt;
	}

	public String getDiscardReason() {
		return discardReason;
	}

	public void setDiscardReason(String discardReason) {
		this.discardReason = discardReason;
	}

}

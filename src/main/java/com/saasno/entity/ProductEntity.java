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

@Entity
@Table(schema = "public", name = "product")
public class ProductEntity {

	@Id
	@Column(length = 64, nullable = false, updatable = false)
	private String id;

	// Diisi ProductRepository.store(), format MMyyyydd + 3 digit urutan harian.
	@Column(length = 16, nullable = false, updatable = false, unique = true)
	private String serial;

	@Column(length = 256, nullable = false)
	private String name;

	@Column(precision = 19, scale = 2, nullable = false)
	private BigDecimal price;

	@Column(length = 32, nullable = false)
	private String unit;

	// ACTIVE | DISABLED
	@Column(length = 16, nullable = false)
	private String status = "ACTIVE";

	@Column(name = "branch_id", length = 64, nullable = false, updatable = false)
	private String branchId;

	@Column(name = "cost_price", precision = 19, scale = 2)
	private BigDecimal costPrice;

	// Keterangan produk (tampil di katalog publik); kosong = belum diisi.
	@Column(length = 1000)
	private String description;

	@Column(nullable = false)
	private LocalDateTime modificated;

	@Column(nullable = false, updatable = false)
	private LocalDateTime registered;

	public ProductEntity() {
		this.id = ID.uuid();
	}

	public ProductEntity(String name, BigDecimal price, String unit) {
		this.id = ID.uuid();
		this.name = name;
		this.price = price;
		this.unit = unit;
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

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public BigDecimal getPrice() {
		return price;
	}

	public void setPrice(BigDecimal price) {
		this.price = price;
	}

	public String getUnit() {
		return unit;
	}

	public void setUnit(String unit) {
		this.unit = unit;
	}

	public String getStatus() {
		return status;
	}

	public void setStatus(String status) {
		this.status = status;
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

	public String getBranchId() {
		return branchId;
	}

	public void setBranchId(String branchId) {
		this.branchId = branchId;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public BigDecimal getCostPrice() {
		return costPrice;
	}

	public void setCostPrice(BigDecimal costPrice) {
		this.costPrice = costPrice;
	}

}

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

// Arti satu baris: 1 {toUnit} = {value} {satuan produk}. Satuan "dari" tidak disimpan karena
// selalu sama dengan ProductEntity.unit, jadi tidak bisa menyimpang saat satuan produk berubah.
@Entity
@Table(schema = "public", name = "product_unit_conversion")
public class ProductUnitConversionEntity {

	@Id
	@Column(length = 64, nullable = false, updatable = false)
	private String id;

	@Column(name = "product_id", length = 64, nullable = false, updatable = false)
	private String productId;

	@Column(name = "to_unit", length = 32, nullable = false)
	private String toUnit;

	@Column(precision = 19, scale = 6, nullable = false)
	private BigDecimal value;

	@Column(nullable = false)
	private LocalDateTime modificated;

	@Column(nullable = false, updatable = false)
	private LocalDateTime registered;

	public ProductUnitConversionEntity() {
		this.id = ID.uuid();
	}

	public ProductUnitConversionEntity(String productId, String toUnit, BigDecimal value) {
		this.id = ID.uuid();
		this.productId = productId;
		this.toUnit = toUnit;
		this.value = value;
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

	public String getToUnit() {
		return toUnit;
	}

	public void setToUnit(String toUnit) {
		this.toUnit = toUnit;
	}

	public BigDecimal getValue() {
		return value;
	}

	public void setValue(BigDecimal value) {
		this.value = value;
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

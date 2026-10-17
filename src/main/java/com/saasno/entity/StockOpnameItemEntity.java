package com.saasno.entity;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

// Semua jumlah dalam satuan dasar produk. Selisih = counted - system.
@Entity
@Table(schema = "public", name = "stock_opname_item")
@IdClass(StockOpnameItemId.class)
public class StockOpnameItemEntity {

	@Id
	@Column(name = "opname_id", length = 64, nullable = false, updatable = false)
	private String opnameId;

	@Id
	@Column(name = "product_id", length = 64, nullable = false, updatable = false)
	private String productId;

	// Saldo sistem saat opname diajukan.
	@Column(name = "system_quantity", precision = 19, scale = 6, nullable = false, updatable = false)
	private BigDecimal systemQuantity;

	// Hasil hitung fisik.
	@Column(name = "counted_quantity", precision = 19, scale = 6, nullable = false, updatable = false)
	private BigDecimal countedQuantity;

	public StockOpnameItemEntity() {
	}

	public StockOpnameItemEntity(String opnameId, String productId, BigDecimal systemQuantity, BigDecimal countedQuantity) {
		this.opnameId = opnameId;
		this.productId = productId;
		this.systemQuantity = systemQuantity;
		this.countedQuantity = countedQuantity;
	}

	public String getOpnameId() {
		return opnameId;
	}

	public void setOpnameId(String opnameId) {
		this.opnameId = opnameId;
	}

	public String getProductId() {
		return productId;
	}

	public void setProductId(String productId) {
		this.productId = productId;
	}

	public BigDecimal getSystemQuantity() {
		return systemQuantity;
	}

	public void setSystemQuantity(BigDecimal systemQuantity) {
		this.systemQuantity = systemQuantity;
	}

	public BigDecimal getCountedQuantity() {
		return countedQuantity;
	}

	public void setCountedQuantity(BigDecimal countedQuantity) {
		this.countedQuantity = countedQuantity;
	}

	public BigDecimal getDifference() {
		return countedQuantity.subtract(systemQuantity);
	}

}

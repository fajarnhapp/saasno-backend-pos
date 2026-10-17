package com.saasno.entity;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

@Entity
@Table(schema = "public", name = "stock_transfer_item")
@IdClass(StockTransferItemId.class)
public class StockTransferItemEntity {

	@Id
	@Column(name = "transfer_id", length = 64, nullable = false, updatable = false)
	private String transferId;

	@Id
	@Column(name = "product_id", length = 64, nullable = false, updatable = false)
	private String productId;

	// Dalam satuan dasar produk.
	@Column(precision = 19, scale = 6, nullable = false, updatable = false)
	private BigDecimal quantity;

	public StockTransferItemEntity() {
	}

	public StockTransferItemEntity(String transferId, String productId, BigDecimal quantity) {
		this.transferId = transferId;
		this.productId = productId;
		this.quantity = quantity;
	}

	public String getTransferId() {
		return transferId;
	}

	public void setTransferId(String transferId) {
		this.transferId = transferId;
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

}

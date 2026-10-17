package com.saasno.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.math.BigDecimal;

@Entity
@Table(schema = "public", name = "sale_return_item")
@IdClass(SaleReturnItemId.class)
public class SaleReturnItemEntity {

	@Id
	@Column(name = "return_id", length = 64, nullable = false, updatable = false)
	private String returnId;

	@Id
	@Column(name = "sale_item_id", length = 64, nullable = false, updatable = false)
	private String saleItemId;

	@Column(name = "quantity", precision = 19, scale = 6, nullable = false, updatable = false)
	private BigDecimal quantity;

	@Column(name = "refund", precision = 19, scale = 2, nullable = false, updatable = false)
	private BigDecimal refund;

	@Column(name = "restock", nullable = false, updatable = false)
	private boolean restock;

	public SaleReturnItemEntity() {
	}

	public SaleReturnItemEntity(String returnId, String saleItemId, BigDecimal quantity, BigDecimal refund, boolean restock) {
		this.restock = restock;
		this.returnId = returnId;
		this.saleItemId = saleItemId;
		this.quantity = quantity;
		this.refund = refund;
	}

	public String getReturnId() {
		return returnId;
	}

	public void setReturnId(String returnId) {
		this.returnId = returnId;
	}

	public String getSaleItemId() {
		return saleItemId;
	}

	public void setSaleItemId(String saleItemId) {
		this.saleItemId = saleItemId;
	}

	public BigDecimal getQuantity() {
		return quantity;
	}

	public void setQuantity(BigDecimal quantity) {
		this.quantity = quantity;
	}

	public BigDecimal getRefund() {
		return refund;
	}

	public void setRefund(BigDecimal refund) {
		this.refund = refund;
	}

	public boolean isRestock() {
		return restock;
	}

	public void setRestock(boolean restock) {
		this.restock = restock;
	}

}

package com.saasno.entity;

import com.saasno.utility.generator.ID;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.math.BigDecimal;

// Baris transaksi; harga, nama, dan serial produk dibekukan saat transaksi.
@Entity
@Table(schema = "public", name = "sale_item")
public class SaleItemEntity {

	@Id
	@Column(name = "id", length = 64, nullable = false, updatable = false)
	private String id;

	@Column(name = "sale_id", length = 64, nullable = false, updatable = false)
	private String saleId;

	@Column(name = "product_id", length = 64, nullable = false, updatable = false)
	private String productId;

	@Column(name = "product_serial", length = 16, nullable = false, updatable = false)
	private String productSerial;

	@Column(name = "product_name", length = 256, nullable = false, updatable = false)
	private String productName;

	@Column(name = "unit", length = 32, nullable = false, updatable = false)
	private String unit;

	@Column(name = "quantity", precision = 19, scale = 6, nullable = false, updatable = false)
	private BigDecimal quantity;

	@Column(name = "unit_price", precision = 19, scale = 2, nullable = false, updatable = false)
	private BigDecimal unitPrice;

	@Column(name = "price_source", length = 16, nullable = false, updatable = false)
	private String priceSource;

	@Column(name = "tier_min_quantity", precision = 19, scale = 6, updatable = false)
	private BigDecimal tierMinQuantity;

	@Column(name = "unit_cost", precision = 19, scale = 2, updatable = false)
	private BigDecimal unitCost;

	@Column(name = "discount", precision = 19, scale = 2, nullable = false, updatable = false)
	private BigDecimal discount;

	@Column(name = "line_total", precision = 19, scale = 2, nullable = false, updatable = false)
	private BigDecimal lineTotal;

	@Column(name = "net_total", precision = 19, scale = 2, nullable = false, updatable = false)
	private BigDecimal netTotal;

	@Column(name = "is_free", nullable = false, updatable = false)
	private boolean free;

	@Column(name = "returned_quantity", precision = 19, scale = 6, nullable = false)
	private BigDecimal returnedQuantity;

	@Column(name = "returned_amount", precision = 19, scale = 2, nullable = false)
	private BigDecimal returnedAmount;

	public SaleItemEntity() {
		this.id = ID.uuid();
		this.returnedQuantity = BigDecimal.ZERO;
		this.returnedAmount = BigDecimal.ZERO;
	}


	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getSaleId() {
		return saleId;
	}

	public void setSaleId(String saleId) {
		this.saleId = saleId;
	}

	public String getProductId() {
		return productId;
	}

	public void setProductId(String productId) {
		this.productId = productId;
	}

	public String getProductSerial() {
		return productSerial;
	}

	public void setProductSerial(String productSerial) {
		this.productSerial = productSerial;
	}

	public String getProductName() {
		return productName;
	}

	public void setProductName(String productName) {
		this.productName = productName;
	}

	public String getUnit() {
		return unit;
	}

	public void setUnit(String unit) {
		this.unit = unit;
	}

	public BigDecimal getQuantity() {
		return quantity;
	}

	public void setQuantity(BigDecimal quantity) {
		this.quantity = quantity;
	}

	public BigDecimal getUnitPrice() {
		return unitPrice;
	}

	public void setUnitPrice(BigDecimal unitPrice) {
		this.unitPrice = unitPrice;
	}

	public String getPriceSource() {
		return priceSource;
	}

	public void setPriceSource(String priceSource) {
		this.priceSource = priceSource;
	}

	public BigDecimal getTierMinQuantity() {
		return tierMinQuantity;
	}

	public void setTierMinQuantity(BigDecimal tierMinQuantity) {
		this.tierMinQuantity = tierMinQuantity;
	}

	public BigDecimal getDiscount() {
		return discount;
	}

	public void setDiscount(BigDecimal discount) {
		this.discount = discount;
	}

	public BigDecimal getLineTotal() {
		return lineTotal;
	}

	public void setLineTotal(BigDecimal lineTotal) {
		this.lineTotal = lineTotal;
	}

	public BigDecimal getNetTotal() {
		return netTotal;
	}

	public void setNetTotal(BigDecimal netTotal) {
		this.netTotal = netTotal;
	}

	public boolean isFree() {
		return free;
	}

	public void setFree(boolean free) {
		this.free = free;
	}

	public BigDecimal getReturnedQuantity() {
		return returnedQuantity;
	}

	public void setReturnedQuantity(BigDecimal returnedQuantity) {
		this.returnedQuantity = returnedQuantity;
	}

	public BigDecimal getReturnedAmount() {
		return returnedAmount;
	}

	public void setReturnedAmount(BigDecimal returnedAmount) {
		this.returnedAmount = returnedAmount;
	}

	public BigDecimal getUnitCost() {
		return unitCost;
	}

	public void setUnitCost(BigDecimal unitCost) {
		this.unitCost = unitCost;
	}

}

package com.saasno.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.hibernate.annotations.BatchSize;

import com.saasno.utility.generator.ID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

// Promo = syarat + hadiah.
//  - scope PRODUCT: syarat dihitung dari produk productId saja; TOTAL: dari seluruh pembelian.
//  - conditionType QUANTITY: minValue = jumlah minimal (satuan produk; untuk TOTAL = total jumlah
//    barang); AMOUNT: minValue = nilai belanja minimal dalam rupiah.
//  - rewardType DISCOUNT_FIXED: potongan discountValue rupiah; DISCOUNT_PERCENT: discountValue persen;
//    FREE_PRODUCT: gratis rewardQuantity unit rewardProductId.
@Entity
@Table(schema = "public", name = "promo")
public class PromoEntity {

	@Id
	@Column(length = 64, nullable = false, updatable = false)
	private String id;

	// Diisi PromoRepository.store(), format MMyyyydd + 3 digit urutan harian.
	@Column(length = 16, nullable = false, updatable = false, unique = true)
	private String serial;

	@Column(length = 256, nullable = false)
	private String name;

	@Column(length = 16, nullable = false)
	private String status;

	@Column(length = 16, nullable = false)
	private String scope;

	@Column(name = "product_id", length = 64)
	private String productId;

	@Column(name = "condition_type", length = 16, nullable = false)
	private String conditionType;

	@Column(name = "min_value", precision = 19, scale = 6, nullable = false)
	private BigDecimal minValue;

	@Column(name = "reward_type", length = 16, nullable = false)
	private String rewardType;

	@Column(name = "discount_value", precision = 19, scale = 2)
	private BigDecimal discountValue;

	@Column(name = "reward_product_id", length = 64)
	private String rewardProductId;

	@Column(name = "reward_quantity", precision = 19, scale = 6)
	private BigDecimal rewardQuantity;

	// Siapa yang boleh memakai promo: ALL (semua customer), TYPE (satu tipe customer, lihat customerType),
	// atau CUSTOMER (customer tertentu, lihat customers).
	@Column(length = 16, nullable = false)
	private String audience = "ALL";

	@Column(name = "customer_type", length = 16)
	private String customerType;

	@ManyToMany(fetch = FetchType.LAZY)
	@JoinTable(schema = "public", name = "promo_customer",
			joinColumns = @JoinColumn(name = "promo_id"),
			inverseJoinColumns = @JoinColumn(name = "customer_id"))
	@BatchSize(size = 50)
	private List<CustomerEntity> customers = new ArrayList<>();

	@Column(name = "valid_from")
	private LocalDate validFrom;

	@Column(name = "valid_until")
	private LocalDate validUntil;

	@Column(name = "branch_id", length = 64, nullable = false, updatable = false)
	private String branchId;

	@Column(nullable = false)
	private LocalDateTime modificated;

	@Column(nullable = false, updatable = false)
	private LocalDateTime registered;

	public PromoEntity() {
		this.id = ID.uuid();
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

	public String getStatus() {
		return status;
	}

	public void setStatus(String status) {
		this.status = status;
	}

	public String getScope() {
		return scope;
	}

	public void setScope(String scope) {
		this.scope = scope;
	}

	public String getProductId() {
		return productId;
	}

	public void setProductId(String productId) {
		this.productId = productId;
	}

	public String getConditionType() {
		return conditionType;
	}

	public void setConditionType(String conditionType) {
		this.conditionType = conditionType;
	}

	public BigDecimal getMinValue() {
		return minValue;
	}

	public void setMinValue(BigDecimal minValue) {
		this.minValue = minValue;
	}

	public String getRewardType() {
		return rewardType;
	}

	public void setRewardType(String rewardType) {
		this.rewardType = rewardType;
	}

	public BigDecimal getDiscountValue() {
		return discountValue;
	}

	public void setDiscountValue(BigDecimal discountValue) {
		this.discountValue = discountValue;
	}

	public String getRewardProductId() {
		return rewardProductId;
	}

	public void setRewardProductId(String rewardProductId) {
		this.rewardProductId = rewardProductId;
	}

	public BigDecimal getRewardQuantity() {
		return rewardQuantity;
	}

	public void setRewardQuantity(BigDecimal rewardQuantity) {
		this.rewardQuantity = rewardQuantity;
	}

	public String getAudience() {
		return audience;
	}

	public void setAudience(String audience) {
		this.audience = audience;
	}

	public String getCustomerType() {
		return customerType;
	}

	public void setCustomerType(String customerType) {
		this.customerType = customerType;
	}

	public List<CustomerEntity> getCustomers() {
		return customers;
	}

	public void setCustomers(List<CustomerEntity> customers) {
		this.customers = customers;
	}

	public LocalDate getValidFrom() {
		return validFrom;
	}

	public void setValidFrom(LocalDate validFrom) {
		this.validFrom = validFrom;
	}

	public LocalDate getValidUntil() {
		return validUntil;
	}

	public void setValidUntil(LocalDate validUntil) {
		this.validUntil = validUntil;
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

}

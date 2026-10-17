package com.saasno.utility.dto.promo;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import jakarta.validation.constraints.NotBlank;

// Field yang diisi sistem (read-only, diabaikan saat create/update): id, serial, productName,
// productUnit, rewardProductName, rewardProductUnit, modificated, registered.
// Update (PUT) bersifat menggantikan seluruh data promo, bukan parsial.
@JsonIgnoreProperties(ignoreUnknown = true)
public class PromoDTO {

	private String id;
	private String branchId;
	private String branchName;
	private String serial;

	@NotBlank(message = "Nama tidak boleh kosong")
	private String name;

	// ACTIVE | DISABLED
	private String status;

	// PRODUCT (syarat dihitung dari satu produk) | TOTAL (dari seluruh pembelian)
	private String scope;
	private String productId;
	private String productName;
	private String productUnit;

	// QUANTITY (minimal jumlah) | AMOUNT (minimal nilai belanja, rupiah)
	private String conditionType;
	private BigDecimal minValue;

	// DISCOUNT_FIXED (rupiah) | DISCOUNT_PERCENT | FREE_PRODUCT
	private String rewardType;
	private BigDecimal discountValue;
	private String rewardProductId;
	private String rewardProductName;
	private String rewardProductUnit;
	private BigDecimal rewardQuantity;

	// Sasaran promo: ALL | TYPE (isi customerType: INDIVIDU/BISNIS) | CUSTOMER (isi customers, cukup id).
	private String audience;
	private String customerType;
	private List<PromoCustomerDTO> customers;

	// Periode berlaku (inklusif), boleh kosong = tanpa batas.
	private LocalDate validFrom;
	private LocalDate validUntil;

	private LocalDateTime modificated;
	private LocalDateTime registered;

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

	public String getProductName() {
		return productName;
	}

	public void setProductName(String productName) {
		this.productName = productName;
	}

	public String getProductUnit() {
		return productUnit;
	}

	public void setProductUnit(String productUnit) {
		this.productUnit = productUnit;
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

	public String getRewardProductName() {
		return rewardProductName;
	}

	public void setRewardProductName(String rewardProductName) {
		this.rewardProductName = rewardProductName;
	}

	public String getRewardProductUnit() {
		return rewardProductUnit;
	}

	public void setRewardProductUnit(String rewardProductUnit) {
		this.rewardProductUnit = rewardProductUnit;
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

	public List<PromoCustomerDTO> getCustomers() {
		return customers;
	}

	public void setCustomers(List<PromoCustomerDTO> customers) {
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

	public String getBranchName() {
		return branchName;
	}

	public void setBranchName(String branchName) {
		this.branchName = branchName;
	}

}

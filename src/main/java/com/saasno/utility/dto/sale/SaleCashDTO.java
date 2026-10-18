package com.saasno.utility.dto.sale;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

// Kas harian satu cabang. net = salesTotal - creditTotal + collectionTotal - voidTotal - returnTotal (uang bersih yang masuk hari itu;
// voidTotal dan returnTotal hanya bagian yang dibayar tunai, tidak termasuk yang mengurangi piutang).
// Penjualan dihitung pada hari selesai (hari disetujui bila perlu persetujuan), void pada hari void, dan retur
// pada hari retur. promoDiscount, manualDiscount, dan tax hanya keterangan dari transaksi selesai hari itu.
@JsonIgnoreProperties(ignoreUnknown = true)
public class SaleCashDTO {

	private String branchId;
	private String branchName;
	private LocalDate date;
	private Long saleCount;
	private BigDecimal salesTotal;
	private Long voidCount;
	private BigDecimal voidTotal;
	private Long returnCount;
	private BigDecimal returnTotal;
	private BigDecimal net;
	private BigDecimal promoDiscount;
	private BigDecimal manualDiscount;
	private BigDecimal chargeTotal;
	// Bagian penjualan yang menjadi piutang (belum diterima) dan pembayaran piutang yang diterima hari itu.
	private BigDecimal creditTotal;
	private BigDecimal collectionTotal;

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

	public LocalDate getDate() {
		return date;
	}

	public void setDate(LocalDate date) {
		this.date = date;
	}

	public Long getSaleCount() {
		return saleCount;
	}

	public void setSaleCount(Long saleCount) {
		this.saleCount = saleCount;
	}

	public BigDecimal getSalesTotal() {
		return salesTotal;
	}

	public void setSalesTotal(BigDecimal salesTotal) {
		this.salesTotal = salesTotal;
	}

	public Long getVoidCount() {
		return voidCount;
	}

	public void setVoidCount(Long voidCount) {
		this.voidCount = voidCount;
	}

	public BigDecimal getVoidTotal() {
		return voidTotal;
	}

	public void setVoidTotal(BigDecimal voidTotal) {
		this.voidTotal = voidTotal;
	}

	public Long getReturnCount() {
		return returnCount;
	}

	public void setReturnCount(Long returnCount) {
		this.returnCount = returnCount;
	}

	public BigDecimal getReturnTotal() {
		return returnTotal;
	}

	public void setReturnTotal(BigDecimal returnTotal) {
		this.returnTotal = returnTotal;
	}

	public BigDecimal getNet() {
		return net;
	}

	public void setNet(BigDecimal net) {
		this.net = net;
	}

	public BigDecimal getPromoDiscount() {
		return promoDiscount;
	}

	public void setPromoDiscount(BigDecimal promoDiscount) {
		this.promoDiscount = promoDiscount;
	}

	public BigDecimal getManualDiscount() {
		return manualDiscount;
	}

	public void setManualDiscount(BigDecimal manualDiscount) {
		this.manualDiscount = manualDiscount;
	}

	public BigDecimal getChargeTotal() {
		return chargeTotal;
	}

	public void setChargeTotal(BigDecimal chargeTotal) {
		this.chargeTotal = chargeTotal;
	}

	public BigDecimal getCreditTotal() {
		return creditTotal;
	}

	public void setCreditTotal(BigDecimal creditTotal) {
		this.creditTotal = creditTotal;
	}

	public BigDecimal getCollectionTotal() {
		return collectionTotal;
	}

	public void setCollectionTotal(BigDecimal collectionTotal) {
		this.collectionTotal = collectionTotal;
	}
}

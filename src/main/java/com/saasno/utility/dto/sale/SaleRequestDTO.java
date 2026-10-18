package com.saasno.utility.dto.sale;

import java.math.BigDecimal;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

// Keranjang. branchId hanya dipakai user global (user cabang selalu cabangnya sendiri). discount = diskon manual
// untuk seluruh transaksi (rupiah utuh). requestKey: kunci unik per pengiriman supaya klik ganda tidak membuat
// dua transaksi. holdId: keranjang tertahan yang dilanjutkan (menjadi terpakai saat transaksi tersimpan).
// Harga, promo, pajak, dan total selalu dihitung server.
@JsonIgnoreProperties(ignoreUnknown = true)
public class SaleRequestDTO {

	private String branchId;
	private String customerId;
	private List<SaleRequestItemDTO> items;
	private BigDecimal discount;
	private String discountReason;
	private BigDecimal paid;
	private String requestKey;
	private String holdId;
	// Kode voucher dan jumlah poin yang dipakai sebagai potongan (opsional).
	private String voucherCode;
	private Long redeemPoints;
	// Penjualan tempo: paid = uang muka (boleh 0), sisanya menjadi piutang yang jatuh tempo pada dueDate (kosong = bawaan).
	private boolean credit;
	private java.time.LocalDate dueDate;

	public String getBranchId() {
		return branchId;
	}

	public void setBranchId(String branchId) {
		this.branchId = branchId;
	}

	public String getCustomerId() {
		return customerId;
	}

	public void setCustomerId(String customerId) {
		this.customerId = customerId;
	}

	public List<SaleRequestItemDTO> getItems() {
		return items;
	}

	public void setItems(List<SaleRequestItemDTO> items) {
		this.items = items;
	}

	public BigDecimal getDiscount() {
		return discount;
	}

	public void setDiscount(BigDecimal discount) {
		this.discount = discount;
	}

	public String getDiscountReason() {
		return discountReason;
	}

	public void setDiscountReason(String discountReason) {
		this.discountReason = discountReason;
	}

	public BigDecimal getPaid() {
		return paid;
	}

	public void setPaid(BigDecimal paid) {
		this.paid = paid;
	}

	public String getRequestKey() {
		return requestKey;
	}

	public void setRequestKey(String requestKey) {
		this.requestKey = requestKey;
	}

	public String getHoldId() {
		return holdId;
	}

	public void setHoldId(String holdId) {
		this.holdId = holdId;
	}

	public String getVoucherCode() {
		return voucherCode;
	}

	public void setVoucherCode(String voucherCode) {
		this.voucherCode = voucherCode;
	}

	public Long getRedeemPoints() {
		return redeemPoints;
	}

	public void setRedeemPoints(Long redeemPoints) {
		this.redeemPoints = redeemPoints;
	}

	public boolean isCredit() {
		return credit;
	}

	public void setCredit(boolean credit) {
		this.credit = credit;
	}

	public java.time.LocalDate getDueDate() {
		return dueDate;
	}

	public void setDueDate(java.time.LocalDate dueDate) {
		this.dueDate = dueDate;
	}
}

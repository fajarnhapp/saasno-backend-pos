package com.saasno.utility.dto.finance;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

// Laporan keuangan harian satu cabang. revenue = penjualan bersih tanpa pajak (penjualan - void - retur); cogs = harga pokok
// barang terjual (dikurangi barang retur yang kembali ke stok); grossProfit = revenue - cogs; operatingResult = grossProfit -
// cashExpense - stockUseExpense. unknownCostLines = baris yang harga pokoknya belum diketahui (laba kotor tidak lengkap).
@JsonIgnoreProperties(ignoreUnknown = true)
public class FinanceReportDTO {

	private String branchId;
	private String branchName;
	private LocalDate date;
	private BigDecimal revenue;
	private BigDecimal cogs;
	private BigDecimal grossProfit;
	private BigDecimal cashExpense;
	private BigDecimal stockUseExpense;
	private BigDecimal operatingResult;
	private BigDecimal pettyCashTopUp;
	private Long unknownCostLines;

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

	public BigDecimal getRevenue() {
		return revenue;
	}

	public void setRevenue(BigDecimal revenue) {
		this.revenue = revenue;
	}

	public BigDecimal getCogs() {
		return cogs;
	}

	public void setCogs(BigDecimal cogs) {
		this.cogs = cogs;
	}

	public BigDecimal getGrossProfit() {
		return grossProfit;
	}

	public void setGrossProfit(BigDecimal grossProfit) {
		this.grossProfit = grossProfit;
	}

	public BigDecimal getCashExpense() {
		return cashExpense;
	}

	public void setCashExpense(BigDecimal cashExpense) {
		this.cashExpense = cashExpense;
	}

	public BigDecimal getStockUseExpense() {
		return stockUseExpense;
	}

	public void setStockUseExpense(BigDecimal stockUseExpense) {
		this.stockUseExpense = stockUseExpense;
	}

	public BigDecimal getOperatingResult() {
		return operatingResult;
	}

	public void setOperatingResult(BigDecimal operatingResult) {
		this.operatingResult = operatingResult;
	}

	public BigDecimal getPettyCashTopUp() {
		return pettyCashTopUp;
	}

	public void setPettyCashTopUp(BigDecimal pettyCashTopUp) {
		this.pettyCashTopUp = pettyCashTopUp;
	}

	public Long getUnknownCostLines() {
		return unknownCostLines;
	}

	public void setUnknownCostLines(Long unknownCostLines) {
		this.unknownCostLines = unknownCostLines;
	}

}

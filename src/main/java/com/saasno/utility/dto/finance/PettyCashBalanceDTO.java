package com.saasno.utility.dto.finance;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

// Saldo kas kecil satu cabang (boleh minus).
@JsonIgnoreProperties(ignoreUnknown = true)
public class PettyCashBalanceDTO {

	private String branchId;
	private String branchName;
	private BigDecimal balance;

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

	public BigDecimal getBalance() {
		return balance;
	}

	public void setBalance(BigDecimal balance) {
		this.balance = balance;
	}

}

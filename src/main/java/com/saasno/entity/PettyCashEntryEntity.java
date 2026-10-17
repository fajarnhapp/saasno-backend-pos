package com.saasno.entity;

import com.saasno.utility.generator.ID;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;

// Ledger kas kecil (append-only, dijaga trigger di database). amount bertanda: + isi kas / pembatalan biaya, - biaya.
@Entity
@Table(schema = "public", name = "petty_cash_entry")
public class PettyCashEntryEntity {

	public static final String TOPUP = "TOPUP";
	public static final String EXPENSE = "EXPENSE";
	public static final String EXPENSE_VOID = "EXPENSE_VOID";

	@Id
	@Column(name = "id", length = 64, nullable = false, updatable = false)
	private String id;

	@Column(name = "branch_id", length = 64, nullable = false, updatable = false)
	private String branchId;

	@Column(name = "type", length = 16, nullable = false, updatable = false)
	private String type;

	@Column(name = "amount", precision = 19, scale = 2, nullable = false, updatable = false)
	private BigDecimal amount;

	@Column(name = "balance_after", precision = 19, scale = 2, nullable = false, updatable = false)
	private BigDecimal balanceAfter;

	@Column(name = "expense_id", length = 64, updatable = false)
	private String expenseId;

	@Column(name = "note", length = 500, updatable = false)
	private String note;

	@Column(name = "created_by", length = 64, nullable = false, updatable = false)
	private String createdBy;

	@Column(name = "registered", nullable = false, updatable = false)
	private LocalDateTime registered;

	public PettyCashEntryEntity() {
		this.id = ID.uuid();
	}

	@PrePersist
	public void beforeInsert() {
		this.registered = LocalDateTime.now();
	}

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getBranchId() {
		return branchId;
	}

	public void setBranchId(String branchId) {
		this.branchId = branchId;
	}

	public String getType() {
		return type;
	}

	public void setType(String type) {
		this.type = type;
	}

	public BigDecimal getAmount() {
		return amount;
	}

	public void setAmount(BigDecimal amount) {
		this.amount = amount;
	}

	public BigDecimal getBalanceAfter() {
		return balanceAfter;
	}

	public void setBalanceAfter(BigDecimal balanceAfter) {
		this.balanceAfter = balanceAfter;
	}

	public String getExpenseId() {
		return expenseId;
	}

	public void setExpenseId(String expenseId) {
		this.expenseId = expenseId;
	}

	public String getNote() {
		return note;
	}

	public void setNote(String note) {
		this.note = note;
	}

	public String getCreatedBy() {
		return createdBy;
	}

	public void setCreatedBy(String createdBy) {
		this.createdBy = createdBy;
	}

	public LocalDateTime getRegistered() {
		return registered;
	}

	public void setRegistered(LocalDateTime registered) {
		this.registered = registered;
	}

}

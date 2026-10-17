package com.saasno.entity;

import java.io.Serializable;
import java.util.Objects;

public class StockBalanceId implements Serializable {

	private static final long serialVersionUID = 1L;

	private String branchId;
	private String productId;

	public StockBalanceId() {
	}

	public StockBalanceId(String branchId, String productId) {
		this.branchId = branchId;
		this.productId = productId;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof StockBalanceId other)) {
			return false;
		}
		return Objects.equals(branchId, other.branchId) && Objects.equals(productId, other.productId);
	}

	@Override
	public int hashCode() {
		return Objects.hash(branchId, productId);
	}

}

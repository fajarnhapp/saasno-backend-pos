package com.saasno.entity;

import java.io.Serializable;
import java.util.Objects;

public class SaleReturnItemId implements Serializable {

	private static final long serialVersionUID = 1L;

	private String returnId;
	private String saleItemId;

	public SaleReturnItemId() {
	}

	public SaleReturnItemId(String returnId, String saleItemId) {
		this.returnId = returnId;
		this.saleItemId = saleItemId;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof SaleReturnItemId other)) {
			return false;
		}
		return Objects.equals(returnId, other.returnId) && Objects.equals(saleItemId, other.saleItemId);
	}

	@Override
	public int hashCode() {
		return Objects.hash(returnId, saleItemId);
	}

}

package com.saasno.entity;

import java.io.Serializable;
import java.util.Objects;

public class StockOpnameItemId implements Serializable {

	private static final long serialVersionUID = 1L;

	private String opnameId;
	private String productId;

	public StockOpnameItemId() {
	}

	public StockOpnameItemId(String opnameId, String productId) {
		this.opnameId = opnameId;
		this.productId = productId;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof StockOpnameItemId other)) {
			return false;
		}
		return Objects.equals(opnameId, other.opnameId) && Objects.equals(productId, other.productId);
	}

	@Override
	public int hashCode() {
		return Objects.hash(opnameId, productId);
	}

}

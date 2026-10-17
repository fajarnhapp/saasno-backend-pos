package com.saasno.entity;

import java.io.Serializable;
import java.util.Objects;

public class StockTransferItemId implements Serializable {

	private static final long serialVersionUID = 1L;

	private String transferId;
	private String productId;

	public StockTransferItemId() {
	}

	public StockTransferItemId(String transferId, String productId) {
		this.transferId = transferId;
		this.productId = productId;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof StockTransferItemId other)) {
			return false;
		}
		return Objects.equals(transferId, other.transferId) && Objects.equals(productId, other.productId);
	}

	@Override
	public int hashCode() {
		return Objects.hash(transferId, productId);
	}

}

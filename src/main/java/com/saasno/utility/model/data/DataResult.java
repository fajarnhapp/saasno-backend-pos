package com.saasno.utility.model.data;

import java.math.BigInteger;
import java.util.List;

public class DataResult<E> {

	private List<E> data;
	private BigInteger total;
	private BigInteger grandTotal;

	public DataResult() {

	}

	public DataResult(BigInteger total, BigInteger grandTotal) {
		this.total = total;
		this.grandTotal = grandTotal;
	}

	public DataResult(List<E> data, BigInteger total, BigInteger grandTotal) {
		this.data = data;
		this.total = total;
		this.grandTotal = grandTotal;
	}

	public DataResult<E> isEmpty() {
		return new DataResult<E>();
	}

	public List<E> getData() {
		return data;
	}

	public void setData(List<E> data) {
		this.data = data;
	}

	public BigInteger getTotal() {
		return total;
	}

	public void setTotal(BigInteger total) {
		this.total = total;
	}

	public BigInteger getGrandTotal() {
		return grandTotal;
	}

	public void setGrandTotal(BigInteger grandTotal) {
		this.grandTotal = grandTotal;
	}

}

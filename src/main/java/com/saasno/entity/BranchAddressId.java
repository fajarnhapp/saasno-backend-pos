package com.saasno.entity;

import java.io.Serializable;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
public class BranchAddressId implements Serializable {

	private static final long serialVersionUID = 1L;

	@Column(name = "branch_id")
	private String branchId;

	@Column(name = "address_id")
	private String addressId;

	public BranchAddressId() {
	}

	public BranchAddressId(String branchId, String addressId) {
		this.branchId = branchId;
		this.addressId = addressId;
	}

	public String getBranchId() {
		return branchId;
	}

	public void setBranchId(String branchId) {
		this.branchId = branchId;
	}

	public String getAddressId() {
		return addressId;
	}

	public void setAddressId(String addressId) {
		this.addressId = addressId;
	}

	@Override
	public int hashCode() {
		return Objects.hash(addressId, branchId);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		BranchAddressId other = (BranchAddressId) obj;
		return Objects.equals(addressId, other.addressId) && Objects.equals(branchId, other.branchId);
	}

}

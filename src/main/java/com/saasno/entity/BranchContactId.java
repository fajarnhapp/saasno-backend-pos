package com.saasno.entity;

import java.io.Serializable;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
public class BranchContactId implements Serializable {

	private static final long serialVersionUID = 1L;

	@Column(name = "branch_id")
	private String branchId;

	@Column(name = "contact_id")
	private String contactId;

	public BranchContactId() {
	}

	public BranchContactId(String branchId, String contactId) {
		this.branchId = branchId;
		this.contactId = contactId;
	}

	public String getBranchId() {
		return branchId;
	}

	public void setBranchId(String branchId) {
		this.branchId = branchId;
	}

	public String getContactId() {
		return contactId;
	}

	public void setContactId(String contactId) {
		this.contactId = contactId;
	}

	@Override
	public int hashCode() {
		return Objects.hash(contactId, branchId);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		BranchContactId other = (BranchContactId) obj;
		return Objects.equals(contactId, other.contactId) && Objects.equals(branchId, other.branchId);
	}

}

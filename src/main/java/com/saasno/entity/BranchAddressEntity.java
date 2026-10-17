package com.saasno.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.NamedQueries;
import jakarta.persistence.NamedQuery;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

@Entity
@Table(schema = "public", name = "branch_address")
@NamedQueries({
		@NamedQuery(name = "BranchAddressEntity.findById", query = "SELECT ba FROM BranchAddressEntity ba WHERE ba.branch.id = :branchId AND ba.address.id = :addressId")
})
public class BranchAddressEntity {

	@EmbeddedId
	private BranchAddressId id = new BranchAddressId();

	@ManyToOne(fetch = FetchType.LAZY)
	@MapsId("branchId")
	@JoinColumn(name = "branch_id")
	private BranchEntity branch;

	@ManyToOne(fetch = FetchType.LAZY)
	@MapsId("addressId")
	@JoinColumn(name = "address_id")
	private AddressEntity address;

	@Column(name = "registered", nullable = false, updatable = false)
	private LocalDateTime registered;

	@PrePersist
	public void beforeInsert() {
		this.id = new BranchAddressId(this.branch.getId(), this.address.getId());
		this.registered = LocalDateTime.now();
	}

	public BranchAddressId getId() {
		return id;
	}

	public void setId(BranchAddressId id) {
		this.id = id;
	}

	public BranchEntity getBranch() {
		return branch;
	}

	public void setBranch(BranchEntity branch) {
		this.branch = branch;
	}

	public AddressEntity getAddress() {
		return address;
	}

	public void setAddress(AddressEntity address) {
		this.address = address;
	}

	public LocalDateTime getRegistered() {
		return registered;
	}

	public void setRegistered(LocalDateTime registered) {
		this.registered = registered;
	}

}

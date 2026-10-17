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
@Table(schema = "public", name = "branch_contact")
@NamedQueries({
		@NamedQuery(name = "BranchContactEntity.findById", query = "SELECT bc FROM BranchContactEntity bc WHERE bc.branch.id = :branchId AND bc.contact.id = :contactId")
})
public class BranchContactEntity {

	@EmbeddedId
	private BranchContactId id = new BranchContactId();

	@ManyToOne(fetch = FetchType.LAZY)
	@MapsId("branchId")
	@JoinColumn(name = "branch_id")
	private BranchEntity branch;

	@ManyToOne(fetch = FetchType.LAZY)
	@MapsId("contactId")
	@JoinColumn(name = "contact_id")
	private ContactEntity contact;

	@Column(name = "registered", nullable = false, updatable = false)
	private LocalDateTime registered;

	@PrePersist
	public void beforeInsert() {
		this.id = new BranchContactId(this.branch.getId(), this.contact.getId());
		this.registered = LocalDateTime.now();
	}

	public BranchContactId getId() {
		return id;
	}

	public void setId(BranchContactId id) {
		this.id = id;
	}

	public BranchEntity getBranch() {
		return branch;
	}

	public void setBranch(BranchEntity branch) {
		this.branch = branch;
	}

	public ContactEntity getContact() {
		return contact;
	}

	public void setContact(ContactEntity contact) {
		this.contact = contact;
	}

	public LocalDateTime getRegistered() {
		return registered;
	}

	public void setRegistered(LocalDateTime registered) {
		this.registered = registered;
	}

}

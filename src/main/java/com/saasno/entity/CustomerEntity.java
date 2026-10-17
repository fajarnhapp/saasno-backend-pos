package com.saasno.entity;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.hibernate.annotations.BatchSize;

import com.saasno.utility.generator.ID;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

// Relasi memakai tabel penghubung: customer_profile (satu profile per customer),
// customer_address dan customer_contact (boleh lebih dari satu). Profile, alamat, dan kontak
// dimiliki customer, jadi ikut tersimpan/terhapus bersamanya.
@Entity
@Table(schema = "public", name = "customer")
public class CustomerEntity {

	@Id
	@Column(length = 64, nullable = false, updatable = false)
	private String id;

	// Diisi CustomerRepository.store(), format MMyyyydd + 3 digit urutan harian.
	@Column(length = 16, nullable = false, updatable = false, unique = true)
	private String serial;

	// Person in charge: penanggung jawab/kontak utama customer.
	@Column(length = 128, nullable = false)
	private String pic;

	// INDIVIDU | BISNIS
	@Column(length = 16, nullable = false)
	private String type;

	// ACTIVE | DISABLED
	@Column(length = 16, nullable = false)
	private String status;

	@OneToOne(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY, optional = false)
	@JoinTable(schema = "public", name = "customer_profile",
			joinColumns = @JoinColumn(name = "customer_id", nullable = false),
			inverseJoinColumns = @JoinColumn(name = "profile_id", nullable = false))
	private ProfileEntity profile;

	@OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
	@JoinTable(schema = "public", name = "customer_address",
			joinColumns = @JoinColumn(name = "customer_id"),
			inverseJoinColumns = @JoinColumn(name = "address_id"))
	@BatchSize(size = 50)
	private List<AddressEntity> addresses = new ArrayList<>();

	@OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
	@JoinTable(schema = "public", name = "customer_contact",
			joinColumns = @JoinColumn(name = "customer_id"),
			inverseJoinColumns = @JoinColumn(name = "contact_id"))
	@BatchSize(size = 50)
	private List<ContactEntity> contacts = new ArrayList<>();

	@Column(name = "branch_id", length = 64, nullable = false, updatable = false)
	private String branchId;

	@Column(nullable = false)
	private LocalDateTime modificated;

	@Column(nullable = false, updatable = false)
	private LocalDateTime registered;

	public CustomerEntity() {
		this.id = ID.uuid();
	}

	@PrePersist
	public void beforeInsert() {
		this.registered = LocalDateTime.now();
		this.modificated = LocalDateTime.now();
	}

	@PreUpdate
	public void beforeUpdate() {
		this.modificated = LocalDateTime.now();
	}

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getSerial() {
		return serial;
	}

	public void setSerial(String serial) {
		this.serial = serial;
	}

	public String getPic() {
		return pic;
	}

	public void setPic(String pic) {
		this.pic = pic;
	}

	public String getType() {
		return type;
	}

	public void setType(String type) {
		this.type = type;
	}

	public String getStatus() {
		return status;
	}

	public void setStatus(String status) {
		this.status = status;
	}

	public ProfileEntity getProfile() {
		return profile;
	}

	public void setProfile(ProfileEntity profile) {
		this.profile = profile;
	}

	public List<AddressEntity> getAddresses() {
		return addresses;
	}

	public void setAddresses(List<AddressEntity> addresses) {
		this.addresses = addresses;
	}

	public List<ContactEntity> getContacts() {
		return contacts;
	}

	public void setContacts(List<ContactEntity> contacts) {
		this.contacts = contacts;
	}

	public LocalDateTime getModificated() {
		return modificated;
	}

	public void setModificated(LocalDateTime modificated) {
		this.modificated = modificated;
	}

	public LocalDateTime getRegistered() {
		return registered;
	}

	public void setRegistered(LocalDateTime registered) {
		this.registered = registered;
	}

	public String getBranchId() {
		return branchId;
	}

	public void setBranchId(String branchId) {
		this.branchId = branchId;
	}

}

package com.saasno.utility.dto.customer;

import java.time.LocalDateTime;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

// Field yang diisi sistem (read-only, diabaikan saat create/update): id, serial, modificated, registered.
// Update (PUT) bersifat menggantikan seluruh data customer (bukan parsial), termasuk profile,
// daftar alamat, dan daftar kontak.
@JsonIgnoreProperties(ignoreUnknown = true)
public class CustomerDTO {

	private String id;
	private String branchId;
	private String branchName;
	private String serial;
	private String pic;
	private String type;
	private String status;
	private CustomerProfileDTO profile;
	private List<CustomerAddressDTO> addresses;
	private List<CustomerContactDTO> contacts;
	private LocalDateTime modificated;
	private LocalDateTime registered;

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

	public CustomerProfileDTO getProfile() {
		return profile;
	}

	public void setProfile(CustomerProfileDTO profile) {
		this.profile = profile;
	}

	public List<CustomerAddressDTO> getAddresses() {
		return addresses;
	}

	public void setAddresses(List<CustomerAddressDTO> addresses) {
		this.addresses = addresses;
	}

	public List<CustomerContactDTO> getContacts() {
		return contacts;
	}

	public void setContacts(List<CustomerContactDTO> contacts) {
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

	public String getBranchName() {
		return branchName;
	}

	public void setBranchName(String branchName) {
		this.branchName = branchName;
	}

}

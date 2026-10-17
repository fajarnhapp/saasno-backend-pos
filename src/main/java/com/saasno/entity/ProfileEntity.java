package com.saasno.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;

import com.saasno.utility.generator.ID;
import com.saasno.utility.picker.Gender;
import com.saasno.utility.picker.Religion;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

// Mengikuti ProfileEntity di annisadua-backend. Kolom STR/SIP/SIP expired tidak ikut karena
// khusus tenaga medis, bukan data customer.
@Entity
@Table(schema = "public", name = "profile")
public class ProfileEntity {

	@Id
	@Column(length = 64, nullable = false, updatable = false)
	private String id;

	@Column(length = 128, nullable = false)
	private String name;

	@Column(length = 32)
	@Enumerated(EnumType.STRING)
	private Gender gender;

	@Column(length = 32)
	@Enumerated(EnumType.STRING)
	private Religion religion;

	@Column(name = "birth_place", length = 128)
	private String birthPlace;

	@Column(name = "birth_date")
	private LocalDate birthDate;

	// Kode jenjang pendidikan 0-9 (TK/Paud sampai S3).
	@Column
	private Integer education;

	@Column(nullable = false)
	private LocalDateTime modificated;

	@Column(nullable = false, updatable = false)
	private LocalDateTime registered;

	public ProfileEntity() {
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

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public Gender getGender() {
		return gender;
	}

	public void setGender(Gender gender) {
		this.gender = gender;
	}

	public Religion getReligion() {
		return religion;
	}

	public void setReligion(Religion religion) {
		this.religion = religion;
	}

	public String getBirthPlace() {
		return birthPlace;
	}

	public void setBirthPlace(String birthPlace) {
		this.birthPlace = birthPlace;
	}

	public LocalDate getBirthDate() {
		return birthDate;
	}

	public void setBirthDate(LocalDate birthDate) {
		this.birthDate = birthDate;
	}

	public Integer getEducation() {
		return education;
	}

	public void setEducation(Integer education) {
		this.education = education;
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

}

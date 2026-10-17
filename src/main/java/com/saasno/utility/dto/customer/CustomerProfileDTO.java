package com.saasno.utility.dto.customer;

import java.time.LocalDate;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

// Data diri/usaha. Untuk customer BISNIS hanya name (nama usaha) yang dipakai; field pribadi diabaikan.
@JsonIgnoreProperties(ignoreUnknown = true)
public class CustomerProfileDTO {

	private String name;
	private String gender;
	private String religion;
	private String birthPlace;
	private LocalDate birthDate;
	private Integer education;

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getGender() {
		return gender;
	}

	public void setGender(String gender) {
		this.gender = gender;
	}

	public String getReligion() {
		return religion;
	}

	public void setReligion(String religion) {
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

}

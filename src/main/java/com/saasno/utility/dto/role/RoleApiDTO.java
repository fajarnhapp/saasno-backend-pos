package com.saasno.utility.dto.role;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import jakarta.validation.constraints.NotBlank;

@JsonIgnoreProperties(ignoreUnknown = true)
public class RoleApiDTO {

	@NotBlank(message = "Role tidak boleh kosong")
	private String roleId;

	@NotBlank(message = "API tidak boleh kosong")
	private String apiId;

	public String getRoleId() {
		return roleId;
	}

	public void setRoleId(String roleId) {
		this.roleId = roleId;
	}

	public String getApiId() {
		return apiId;
	}

	public void setApiId(String apiId) {
		this.apiId = apiId;
	}

}

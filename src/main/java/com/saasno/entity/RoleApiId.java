package com.saasno.entity;

import java.io.Serializable;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
public class RoleApiId implements Serializable {

	private static final long serialVersionUID = 5689396112387834451L;

	@Column(name = "role_id")
	private String roleId;

	@Column(name = "api_id")
	private String apiId;

	public RoleApiId() {
	}

	public RoleApiId(String roleId, String apiId) {
		this.roleId = roleId;
		this.apiId = apiId;
	}

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

	@Override
	public int hashCode() {
		return Objects.hash(apiId, roleId);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		RoleApiId other = (RoleApiId) obj;
		return Objects.equals(apiId, other.apiId) && Objects.equals(roleId, other.roleId);
	}

}

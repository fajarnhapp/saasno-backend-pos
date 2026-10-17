package com.saasno.utility.dto.role;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import jakarta.validation.constraints.NotBlank;

/**
 * Only the changes to a role's assignments (API or menu IDs), applied in a single transaction.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class RoleAssignmentUpdateDTO {

	@NotBlank(message = "Role tidak boleh kosong")
	private String roleId;

	private List<String> assign = new ArrayList<>();

	private List<String> unassign = new ArrayList<>();

	public String getRoleId() {
		return roleId;
	}

	public void setRoleId(String roleId) {
		this.roleId = roleId;
	}

	public List<String> getAssign() {
		return assign;
	}

	public void setAssign(List<String> assign) {
		this.assign = assign == null ? new ArrayList<>() : assign;
	}

	public List<String> getUnassign() {
		return unassign;
	}

	public void setUnassign(List<String> unassign) {
		this.unassign = unassign == null ? new ArrayList<>() : unassign;
	}

}

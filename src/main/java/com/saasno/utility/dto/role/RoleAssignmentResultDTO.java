package com.saasno.utility.dto.role;

import java.util.List;

/**
 * IDs that were actually changed; already-assigned or not-assigned IDs are skipped and not listed.
 */
public class RoleAssignmentResultDTO {

	private String roleId;

	private List<String> assigned;

	private List<String> unassigned;

	public RoleAssignmentResultDTO(String roleId, List<String> assigned, List<String> unassigned) {
		this.roleId = roleId;
		this.assigned = assigned;
		this.unassigned = unassigned;
	}

	public String getRoleId() {
		return roleId;
	}

	public List<String> getAssigned() {
		return assigned;
	}

	public List<String> getUnassigned() {
		return unassigned;
	}

}

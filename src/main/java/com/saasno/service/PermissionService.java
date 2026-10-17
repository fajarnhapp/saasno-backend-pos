package com.saasno.service;

import java.util.HashSet;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.saasno.entity.RoleEntity;
import com.saasno.repository.RoleRepository;
import com.saasno.utility.SessionUtility;

// Izin API user yang sedang login, dari tabel role_api yang sama dengan filter API. Dipakai bila tampilan atau
// isi data bergantung pada izin tertentu (dashboard, artikel).
@Service
public class PermissionService {

	@Autowired
	private RoleRepository roleRepository;

	@Autowired
	private SessionUtility sessionUtility;

	@Transactional(readOnly = true)
	public Set<String> apis() {
		Set<String> ids = new HashSet<>();
		Set<String> roles = sessionUtility.getRoles();
		if (roles != null) {
			for (String roleId : roles) {
				RoleEntity role = roleRepository.find(roleId);
				if (role != null && role.getApis() != null) {
					role.getApis().forEach(api -> ids.add(api.getId()));
				}
			}
		}
		return ids;
	}

	public boolean has(String apiId) {
		return apis().contains(apiId);
	}

}

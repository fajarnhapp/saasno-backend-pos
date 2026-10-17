package com.saasno.service;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.saasno.entity.MenuEntity;
import com.saasno.entity.RoleEntity;
import com.saasno.entity.RoleMenuEntity;
import com.saasno.entity.RoleMenuId;
import com.saasno.repository.RoleMenuRepositoty;
import com.saasno.repository.RoleRepository;
import com.saasno.repository.MenuRepository;
import com.saasno.utility.dto.role.RoleMenuDTO;
import com.saasno.utility.dto.role.RoleAssignmentResultDTO;
import com.saasno.utility.dto.role.RoleAssignmentUpdateDTO;
import com.saasno.utility.exception.ApiException;
import com.saasno.utility.logger.Log;
import com.saasno.utility.mapper.RoleMenuMapper;
import com.saasno.utility.model.ResponseModel;
import com.saasno.utility.model.data.DataFilter;
import com.saasno.utility.model.data.DataResult;
import com.saasno.utility.SessionUtility;

import jakarta.servlet.http.HttpServletRequest;

@Service
public class RoleMenuService {

	@Autowired
	private RoleMenuRepositoty roleMenuRepository;

	@Autowired
	private RoleRepository roleRepository;

	@Autowired
	private MenuRepository menuRepository;

	@Autowired
	private RoleMenuMapper roleMenuMapper;

	@Autowired
	private SessionUtility sessionUtility;

	/**
	 * Apply assign/unassign changes to a role in a single transaction. Assigning a menu the
	 * role already has, or unassigning one it does not have, is skipped instead of failing.
	 */
	@Transactional
	public ResponseModel<RoleAssignmentResultDTO> update(RoleAssignmentUpdateDTO request) {
		RoleEntity role = roleRepository.find(request.getRoleId());
		if (role == null) {
			throw new ApiException("404", "Role tidak ditemukan");
		}

		Set<String> assignIds = new LinkedHashSet<>(request.getAssign());
		Set<String> unassignIds = new LinkedHashSet<>(request.getUnassign());
		for (String menuId : assignIds) {
			if (unassignIds.contains(menuId)) {
				throw new ApiException("400", "Menu tidak boleh dipasang dan dilepas bersamaan: " + menuId);
			}
		}

		List<String> unassigned = new ArrayList<>();
		for (String menuId : unassignIds) {
			RoleMenuEntity existing = roleMenuRepository.findById(new RoleMenuId(role.getId(), menuId));
			if (existing != null) {
				roleMenuRepository.delete(existing);
				unassigned.add(menuId);
			}
		}

		List<String> assigned = new ArrayList<>();
		for (String menuId : assignIds) {
			MenuEntity menu = menuRepository.find(menuId);
			if (menu == null) {
				throw new ApiException("404", "Menu tidak ditemukan: " + menuId);
			}
			if (roleMenuRepository.findById(new RoleMenuId(role.getId(), menuId)) != null) {
				continue;
			}
			RoleMenuEntity entity = new RoleMenuEntity();
			entity.setRole(role);
			entity.setMenu(menu);
			roleMenuRepository.store(entity);
			assigned.add(menuId);
		}

		return new ResponseModel<RoleAssignmentResultDTO>()
				.success(new RoleAssignmentResultDTO(role.getId(), assigned, unassigned));
	}

	@Transactional(readOnly = true)
	public ResponseEntity<DataResult<RoleMenuDTO>> read(HttpServletRequest request) {
		try {
			DataResult<RoleMenuEntity> result = roleMenuRepository.dataTable(new DataFilter(request));
			List<RoleMenuDTO> dto = roleMenuMapper.dto(result.getData());
			return new ResponseEntity<DataResult<RoleMenuDTO>>(
					new DataResult<>(dto, result.getTotal(), result.getGrandTotal()), HttpStatus.OK);
		} catch (Exception e) {
			Log.error(e.getMessage(), e);
			return new ResponseEntity<DataResult<RoleMenuDTO>>(
					new DataResult<RoleMenuDTO>(BigInteger.ZERO, BigInteger.ZERO), HttpStatus.OK);
		}
	}

	@Transactional(readOnly = true)
	public ResponseModel<Boolean> canAccessMenu(String menuId) {
		Set<String> userRoles = sessionUtility.getRoles();
		if (userRoles == null || userRoles.isEmpty()) {
			throw new ApiException("404", "User tidak memiliki role");
		}

		boolean hasAccess = userRoles.stream()
				.anyMatch(roleId -> {
					RoleMenuEntity roleMenu = roleMenuRepository.findById(new RoleMenuId(roleId, menuId));
					return roleMenu != null;
				});

		return new ResponseModel<Boolean>().success(hasAccess);
	}
}

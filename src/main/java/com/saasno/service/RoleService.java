package com.saasno.service;

import java.math.BigInteger;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.saasno.entity.ApiEntity;
import com.saasno.entity.MenuEntity;
import com.saasno.entity.RoleApiEntity;
import com.saasno.entity.RoleEntity;
import com.saasno.entity.RoleMenuEntity;
import com.saasno.repository.ApiRepository;
import com.saasno.repository.MenuRepository;
import com.saasno.repository.RoleApiRepository;
import com.saasno.repository.RoleMenuRepositoty;
import com.saasno.repository.RoleRepository;
import com.saasno.utility.dto.role.RoleDTO;
import com.saasno.utility.exception.ApiException;
import com.saasno.utility.logger.Log;
import com.saasno.utility.mapper.RoleMapper;
import com.saasno.utility.model.ResponseModel;
import com.saasno.utility.model.data.DataFilter;
import com.saasno.utility.model.data.DataResult;

import jakarta.servlet.http.HttpServletRequest;

@Service
public class RoleService {

	// Akses "milik sendiri" (notifikasi, profil, password, cek akses menu) yang wajar
	// dimiliki role apa pun begitu dibuat, tanpa perlu di-assign manual satu-satu.
	private static final List<String> DEFAULT_API_IDS = List.of(
			"GET_API_DASHBOARD",
			"GET_API_DOWNLOAD",
			"POST_API_DOWNLOAD",
			"GET_API_DOWNLOAD_FILE",
			"DELETE_API_DOWNLOAD",
			"GET_API_ARTICLE",
			"GET_API_ARTICLE_ACCESS",
			"GET_API_SEARCH",
			"GET_API_NOTIFICATION_PREFERENCE",
			"PUT_API_NOTIFICATION_PREFERENCE",
			"PUT_API_NOTIFICATION_ME",
			"GET_API_NOTIFICATION_ME",
			"GET_API_USER_ME",
			"GET_API_NOTIFICATION_UNREAD_ME",
			"GET_API_ROLE_MENU_ACCESS",
			"PUT_API_NOTIFICATION_UNREAD_ME",
			"PUT_API_USER_CHANGE_PASSWORD",
			"POST_API_USER_OTP_SETUP",
			"POST_API_USER_OTP_ENABLE",
			"DELETE_API_USER_OTP");

	private static final List<String> DEFAULT_MENU_IDS = List.of("DASHBOARD", "SECURITY", "ARTICLE");

	@Autowired
	private RoleRepository roleRepository;

	@Autowired
	private RoleMapper roleMapper;

	@Autowired
	private ApiRepository apiRepository;

	@Autowired
	private MenuRepository menuRepository;

	@Autowired
	private RoleApiRepository roleApiRepository;

	@Autowired
	private RoleMenuRepositoty roleMenuRepository;

	@Transactional
	public ResponseModel<RoleDTO> create(RoleDTO role) {
		RoleEntity existingRole = roleRepository.find(role.getId());
		if (existingRole != null) {
			throw new ApiException("409", "Role sudah ada");
		}

		RoleEntity entity = roleMapper.entity(role);
		roleRepository.store(entity);

		assignDefaultApis(entity);
		assignDefaultMenu(entity);

		return new ResponseModel<RoleDTO>().success(role);
	}

	private void assignDefaultApis(RoleEntity role) {
		for (String apiId : DEFAULT_API_IDS) {
			ApiEntity api = apiRepository.find(apiId);
			if (api == null) {
				continue;
			}
			RoleApiEntity roleApi = new RoleApiEntity();
			roleApi.setRole(role);
			roleApi.setApi(api);
			roleApiRepository.store(roleApi);
		}
	}

	private void assignDefaultMenu(RoleEntity role) {
		for (String menuId : DEFAULT_MENU_IDS) {
			MenuEntity menu = menuRepository.find(menuId);
			if (menu == null) {
				continue;
			}
			RoleMenuEntity roleMenu = new RoleMenuEntity();
			roleMenu.setRole(role);
			roleMenu.setMenu(menu);
			roleMenuRepository.store(roleMenu);
		}
	}

	@Transactional(readOnly = true)
	public ResponseEntity<DataResult<RoleDTO>> read(HttpServletRequest request) {
		try {
			DataResult<RoleEntity> result = roleRepository.dataTable(new DataFilter(request));
			List<RoleDTO> dto = roleMapper.dto(result.getData());
			return new ResponseEntity<DataResult<RoleDTO>>(
					new DataResult<>(dto, result.getTotal(), result.getGrandTotal()), HttpStatus.OK);
		} catch (Exception e) {
			Log.error(e.getMessage(), e);
			return new ResponseEntity<DataResult<RoleDTO>>(new DataResult<RoleDTO>(BigInteger.ZERO, BigInteger.ZERO),
					HttpStatus.OK);
		}
	}

	@Transactional
	public ResponseModel<RoleDTO> update(RoleDTO role) {
		RoleEntity existingRole = roleRepository.find(role.getId());
		if (existingRole == null) {
			throw new ApiException("404", "Role tidak ditemukan");
		}

		if (role.getName() != null && !existingRole.getName().equals(role.getName())) {
			existingRole.setName(role.getName());
		}

		roleRepository.update(existingRole);
		return new ResponseModel<RoleDTO>().success(role);
	}

	@Transactional
	public ResponseModel<RoleDTO> delete(String id) {
		RoleEntity existingRole = roleRepository.find(id);
		if (existingRole == null) {
			throw new ApiException("404", "Role tidak ditemukan");
		}
		if (roleRepository.countUsers(id) > 0) {
			throw new ApiException("409", "Role masih dipakai oleh user");
		}
		roleRepository.delete(existingRole);
		return new ResponseModel<RoleDTO>().success();
	}
}

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

import com.saasno.entity.ApiEntity;
import com.saasno.entity.RoleApiEntity;
import com.saasno.entity.RoleApiId;
import com.saasno.entity.RoleEntity;
import com.saasno.repository.RoleApiRepository;
import com.saasno.repository.RoleRepository;
import com.saasno.repository.ApiRepository;
import com.saasno.utility.dto.role.RoleApiDTO;
import com.saasno.utility.dto.role.RoleAssignmentResultDTO;
import com.saasno.utility.dto.role.RoleAssignmentUpdateDTO;
import com.saasno.utility.exception.ApiException;
import com.saasno.utility.logger.Log;
import com.saasno.utility.mapper.RoleApiMapper;
import com.saasno.utility.model.ResponseModel;
import com.saasno.utility.model.data.DataFilter;
import com.saasno.utility.model.data.DataResult;

import jakarta.servlet.http.HttpServletRequest;

@Service
public class RoleApiService {

	@Autowired
	private RoleApiRepository roleApiRepository;

	@Autowired
	private RoleRepository roleRepository;

	@Autowired
	private ApiRepository apiRepository;

	@Autowired
	private RoleApiMapper roleApiMapper;

	/**
	 * Apply assign/unassign changes to a role in a single transaction. Assigning an API the
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
		for (String apiId : assignIds) {
			if (unassignIds.contains(apiId)) {
				throw new ApiException("400", "API tidak boleh dipasang dan dilepas bersamaan: " + apiId);
			}
		}

		List<String> unassigned = new ArrayList<>();
		for (String apiId : unassignIds) {
			RoleApiEntity existing = roleApiRepository.findById(new RoleApiId(role.getId(), apiId));
			if (existing != null) {
				roleApiRepository.delete(existing);
				unassigned.add(apiId);
			}
		}

		List<String> assigned = new ArrayList<>();
		for (String apiId : assignIds) {
			ApiEntity api = apiRepository.find(apiId);
			if (api == null) {
				throw new ApiException("404", "API tidak ditemukan: " + apiId);
			}
			if (roleApiRepository.findById(new RoleApiId(role.getId(), apiId)) != null) {
				continue;
			}
			RoleApiEntity entity = new RoleApiEntity();
			entity.setRole(role);
			entity.setApi(api);
			roleApiRepository.store(entity);
			assigned.add(apiId);
		}

		return new ResponseModel<RoleAssignmentResultDTO>()
				.success(new RoleAssignmentResultDTO(role.getId(), assigned, unassigned));
	}

	@Transactional(readOnly = true)
	public ResponseEntity<DataResult<RoleApiDTO>> read(HttpServletRequest request) {
		try {
			DataResult<RoleApiEntity> result = roleApiRepository.dataTable(new DataFilter(request));
			List<RoleApiDTO> dto = roleApiMapper.dto(result.getData());
			return new ResponseEntity<DataResult<RoleApiDTO>>(
					new DataResult<>(dto, result.getTotal(), result.getGrandTotal()), HttpStatus.OK);
		} catch (Exception e) {
			Log.error(e.getMessage(), e);
			return new ResponseEntity<DataResult<RoleApiDTO>>(
					new DataResult<RoleApiDTO>(BigInteger.ZERO, BigInteger.ZERO), HttpStatus.OK);
		}
	}
}

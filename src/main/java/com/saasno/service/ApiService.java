package com.saasno.service;

import java.math.BigInteger;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.saasno.entity.ApiEntity;
import com.saasno.repository.ApiRepository;
import com.saasno.utility.dto.api.ApiDTO;
import com.saasno.utility.exception.ApiException;
import com.saasno.utility.logger.Log;
import com.saasno.utility.mapper.ApiMapper;
import com.saasno.utility.model.ResponseModel;
import com.saasno.utility.model.data.DataFilter;
import com.saasno.utility.model.data.DataResult;

import jakarta.servlet.http.HttpServletRequest;

@Service
public class ApiService {

	@Autowired
	private ApiRepository apiRepository;

	@Autowired
	private ApiMapper apiMapper;

	@Transactional
	public ResponseModel<ApiDTO> create(ApiDTO api) {
		ApiEntity existingApi = apiRepository.find(api.getId());
		if (existingApi != null) {
			throw new ApiException("409", "API sudah ada");
		}

		apiRepository.store(apiMapper.entity(api));
		return new ResponseModel<ApiDTO>().success(api);
	}

	@Transactional(readOnly = true)
	public ResponseEntity<DataResult<ApiDTO>> read(HttpServletRequest request) {
		try {
			DataResult<ApiEntity> result = apiRepository.dataTable(new DataFilter(request));
			List<ApiDTO> dto = apiMapper.dto(result.getData());
			return new ResponseEntity<DataResult<ApiDTO>>(
					new DataResult<>(dto, result.getTotal(), result.getGrandTotal()), HttpStatus.OK);
		} catch (Exception e) {
			Log.error(e.getMessage(), e);
			return new ResponseEntity<DataResult<ApiDTO>>(new DataResult<ApiDTO>(BigInteger.ZERO, BigInteger.ZERO),
					HttpStatus.OK);
		}
	}

	@Transactional
	public ResponseModel<ApiDTO> update(ApiDTO api) {
		ApiEntity existingApi = apiRepository.find(api.getId());
		if (existingApi == null) {
			throw new ApiException("404", "API tidak ditemukan");
		}

		if (api.getName() != null && !api.getName().equals(existingApi.getName())) {
			existingApi.setName(api.getName());
		}

		if (api.getMethod() != null && !existingApi.getMethod().equals(api.getMethod())) {
			existingApi.setMethod(api.getMethod());
		}

		if (api.getPath() != null && !existingApi.getPath().equals(api.getPath())) {
			existingApi.setPath(api.getPath());
		}

		apiRepository.update(existingApi);
		return new ResponseModel<ApiDTO>().success(api);
	}

	@Transactional
	public ResponseModel<ApiDTO> delete(String id) {
		ApiEntity existingApi = apiRepository.find(id);
		if (existingApi == null) {
			throw new ApiException("404", "API tidak ditemukan");
		}
		apiRepository.delete(existingApi);
		return new ResponseModel<ApiDTO>().success();
	}
}

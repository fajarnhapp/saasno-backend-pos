package com.saasno.service;

import java.math.BigInteger;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.saasno.entity.MenuEntity;
import com.saasno.repository.MenuRepository;
import com.saasno.utility.dto.menu.MenuDTO;
import com.saasno.utility.exception.ApiException;
import com.saasno.utility.logger.Log;
import com.saasno.utility.mapper.MenuMapper;
import com.saasno.utility.model.ResponseModel;
import com.saasno.utility.model.data.DataFilter;
import com.saasno.utility.model.data.DataResult;
import com.saasno.utility.verification.StringVerification;

import jakarta.servlet.http.HttpServletRequest;

@Service
public class MenuService {

	@Autowired
	private MenuRepository menuRepository;

	@Autowired
	private MenuMapper menuMapper;

	@Transactional
	public ResponseModel<MenuDTO> create(MenuDTO menu) {
		MenuEntity existingMenu = menuRepository.find(menu.getId());
		if (existingMenu != null) {
			throw new ApiException("409", "Menu sudah ada");
		}

		MenuEntity menuEntity = menuMapper.entity(menu);
		if (menuEntity.getParent() == null || StringVerification.nullOrEmpty(menuEntity.getParent().getId())) {
			menuEntity.setParent(null);
		} else {
			MenuEntity parentMenu = menuRepository.find(menuEntity.getParent().getId());
			if (parentMenu == null) {
				throw new ApiException("404", "Menu induk tidak ditemukan");
			}
			menuEntity.setParent(parentMenu);
		}

		menuRepository.store(menuEntity);
		return new ResponseModel<MenuDTO>().success(menu);
	}

	@Transactional(readOnly = true)
	public ResponseEntity<DataResult<MenuDTO>> read(HttpServletRequest request) {
		try {
			DataResult<MenuEntity> result = menuRepository.dataTable(new DataFilter(request));
			List<MenuDTO> dto = menuMapper.dto(result.getData());
			return new ResponseEntity<DataResult<MenuDTO>>(
					new DataResult<>(dto, result.getTotal(), result.getGrandTotal()), HttpStatus.OK);
		} catch (Exception e) {
			Log.error(e.getMessage(), e);
			return new ResponseEntity<DataResult<MenuDTO>>(new DataResult<MenuDTO>(BigInteger.ZERO, BigInteger.ZERO),
					HttpStatus.OK);
		}
	}

	@Transactional
	public ResponseModel<MenuDTO> update(MenuDTO menu) {
		MenuEntity existingMenu = menuRepository.find(menu.getId());
		if (existingMenu == null) {
			throw new ApiException("404", "Menu tidak ditemukan");
		}

		if (menu.getName() != null && !existingMenu.getName().equals(menu.getName())) {
			existingMenu.setName(menu.getName());
		}

		if (menu.getUrl() != null && !existingMenu.getUrl().equals(menu.getUrl())) {
			existingMenu.setUrl(menu.getUrl());
		}

		if (menu.getIcon() != null && !existingMenu.getIcon().equals(menu.getIcon())) {
			existingMenu.setIcon(menu.getIcon());
		}

		if (menu.getOrderIndex() != null && !existingMenu.getOrderIndex().equals(menu.getOrderIndex())) {
			if (existingMenu.getOrderIndex() == 0) {
				MenuEntity parentMenu = menuRepository.findByParent(menu.getParentId());
				if (parentMenu != null) {
					throw new ApiException("400", "Menu induk masih memiliki menu turunan");
				}
			}

			existingMenu.setOrderIndex(menu.getOrderIndex());
		}

		if (menu.getParentId() != null && menu.getParentId() != null
				&& !existingMenu.getParent().getId().equals(menu.getParentId())) {
			MenuEntity parentMenu = menuRepository.findByParent(menu.getParentId());
			if (parentMenu == null) {
				throw new ApiException("404", "Menu induk tidak ditemukan");
			}

			existingMenu.setParent(parentMenu);
		}

		menuRepository.update(existingMenu);
		return new ResponseModel<MenuDTO>().success(menu);
	}

	@Transactional
	public ResponseModel<MenuDTO> delete(String id) {
		MenuEntity existingMenu = menuRepository.find(id);
		if (existingMenu == null) {
			throw new ApiException("404", "Menu tidak ditemukan");
		}
		menuRepository.delete(existingMenu);
		return new ResponseModel<MenuDTO>().success();
	}
}

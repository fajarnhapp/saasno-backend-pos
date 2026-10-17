package com.saasno.utility.dto.role;

import java.time.LocalDateTime;
import java.util.Set;

import com.saasno.utility.dto.api.ApiDTO;
import com.saasno.utility.dto.menu.MenuDTO;

public class RoleDTO {

	private String id;
	private String name;
	private Set<MenuDTO> menus;
	private Set<ApiDTO> apis;
	private LocalDateTime modificated;
	private LocalDateTime registered;

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public Set<MenuDTO> getMenus() {
		return menus;
	}

	public void setMenus(Set<MenuDTO> menus) {
		this.menus = menus;
	}

	public Set<ApiDTO> getApis() {
		return apis;
	}

	public void setApis(Set<ApiDTO> apis) {
		this.apis = apis;
	}

	public LocalDateTime getModificated() {
		return modificated;
	}

	public void setModificated(LocalDateTime modificated) {
		this.modificated = modificated;
	}

	public LocalDateTime getRegistered() {
		return registered;
	}

	public void setRegistered(LocalDateTime registered) {
		this.registered = registered;
	}

}

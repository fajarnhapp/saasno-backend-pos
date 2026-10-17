package com.saasno.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.NamedQueries;
import jakarta.persistence.NamedQuery;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

@Entity
@Table(name = "role_api")
@NamedQueries({
		@NamedQuery(name = "RoleApiEntity.findById", query = "SELECT ra FROM RoleApiEntity ra WHERE ra.role.id = :roleId AND ra.api.id = :apiId")
})
public class RoleApiEntity {

	@EmbeddedId
	private RoleApiId id = new RoleApiId();

	@ManyToOne(fetch = FetchType.LAZY)
	@MapsId("roleId")
	@JoinColumn(name = "role_id")
	private RoleEntity role;

	@ManyToOne(fetch = FetchType.LAZY)
	@MapsId("apiId")
	@JoinColumn(name = "api_id")
	private ApiEntity api;

	@Column(name = "registered")
	private LocalDateTime registered;

	@PrePersist
	public void beforeInsert() {
		this.id = new RoleApiId(this.role.getId(), this.api.getId());
		this.registered = LocalDateTime.now();
	}

	public RoleApiId getId() {
		return id;
	}

	public void setId(RoleApiId id) {
		this.id = id;
	}

	public RoleEntity getRole() {
		return role;
	}

	public void setRole(RoleEntity role) {
		this.role = role;
	}

	public ApiEntity getApi() {
		return api;
	}

	public void setApi(ApiEntity api) {
		this.api = api;
	}

	public LocalDateTime getRegistered() {
		return registered;
	}

	public void setRegistered(LocalDateTime registered) {
		this.registered = registered;
	}

}

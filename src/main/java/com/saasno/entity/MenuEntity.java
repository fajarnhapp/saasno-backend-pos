package com.saasno.entity;

import java.time.LocalDateTime;
import java.util.Set;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.NamedQueries;
import jakarta.persistence.NamedQuery;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

@Entity
@Table(name = "menu")
@NamedQueries({
		@NamedQuery(name = "MenuEntity.findByParent", query = "SELECT m FROM MenuEntity m WHERE m.parent.id = :parent")
})
public class MenuEntity {

	@Id
	private String id;

	@Column(nullable = false)
	private String name;

	@Column(nullable = true)
	private String icon;

	@Column(nullable = false)
	private String url;

	@Column(name = "order_index")
	private Integer orderIndex;

	@ManyToOne
	@JoinColumn(name = "parent_id")
	private MenuEntity parent;

	@OneToMany(mappedBy = "parent")
	private Set<MenuEntity> children;

	@Column(nullable = false)
	private LocalDateTime modificated;

	@Column(nullable = false, updatable = false)
	private LocalDateTime registered;

	@PrePersist
	public void beforeInsert() {
		this.registered = LocalDateTime.now();
		this.modificated = LocalDateTime.now();
	}

	@PreUpdate
	public void beforeUpdate() {
		this.modificated = LocalDateTime.now();
	}

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

	public String getIcon() {
		return icon;
	}

	public void setIcon(String icon) {
		this.icon = icon;
	}

	public String getUrl() {
		return url;
	}

	public void setUrl(String url) {
		this.url = url;
	}

	public Integer getOrderIndex() {
		return orderIndex;
	}

	public void setOrderIndex(Integer orderIndex) {
		this.orderIndex = orderIndex;
	}

	public MenuEntity getParent() {
		return parent;
	}

	public void setParent(MenuEntity parent) {
		this.parent = parent;
	}

	public Set<MenuEntity> getChildren() {
		return children;
	}

	public void setChildren(Set<MenuEntity> children) {
		this.children = children;
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
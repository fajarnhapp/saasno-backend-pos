package com.saasno.entity;

import java.util.Calendar;
import java.util.Date;
import java.util.Set;
import java.util.stream.Collectors;

import com.saasno.utility.generator.ID;

public class SessionEntity {

	private String id;
	private String userId;
	private String username;
	private Set<String> roles;
	private Date registered;
	private Date expired;
	private boolean twoFactorPending;
	// Asal perangkat saat login (untuk daftar perangkat); kosong pada sesi lama.
	private String ip;
	private String userAgent;

	public SessionEntity() {

	}

	public SessionEntity(String id, String userId, String username, Set<String> roles,
			Date registered, Date expired) {
		this.id = id;
		this.userId = userId;
		this.username = username;
		this.roles = roles;
		this.registered = registered;
		this.expired = expired;
	}

	public SessionEntity(UserEntity user) {
		this.id = user.getUsername() + ":" + ID.uuid();
		this.userId = user.getId();
		this.username = user.getUsername();
		this.roles = user.getRoles().stream().map(RoleEntity::getId).collect(Collectors.toSet());
		this.registered = new Date();
		Calendar calendar = Calendar.getInstance();
		calendar.add(Calendar.SECOND, 86400);
		this.expired = calendar.getTime();
	}

	public SessionEntity(UserEntity user, Integer expiredSeconds) {
		this.id = user.getUsername() + ":" + ID.uuid();
		this.userId = user.getId();
		this.username = user.getUsername();
		this.roles = user.getRoles().stream().map(RoleEntity::getId).collect(Collectors.toSet());
		this.registered = new Date();
		Calendar calendar = Calendar.getInstance();
		calendar.add(Calendar.SECOND, expiredSeconds);
		this.expired = calendar.getTime();
	}

	public SessionEntity(UserEntity user, String prefix) {
		this.id = prefix + user.getUsername() + ":" + ID.uuid();
		this.username = user.getUsername();
		this.roles = user.getRoles().stream().map(RoleEntity::getId).collect(Collectors.toSet());
		this.registered = new Date();
		Calendar calendar = Calendar.getInstance();
		calendar.add(Calendar.SECOND, 600);
		this.expired = calendar.getTime();
	}

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getUserId() {
		return userId;
	}

	public void setUserId(String userId) {
		this.userId = userId;
	}

	public String getUsername() {
		return username;
	}

	public void setUsername(String username) {
		this.username = username;
	}

	public Set<String> getRoles() {
		return roles;
	}

	public void setRoles(Set<String> roles) {
		this.roles = roles;
	}

	public Date getRegistered() {
		return registered;
	}

	public void setRegistered(Date registered) {
		this.registered = registered;
	}

	public Date getExpired() {
		return expired;
	}

	public void setExpired(Date expired) {
		this.expired = expired;
	}

	public boolean isTwoFactorPending() {
		return twoFactorPending;
	}

	public void setTwoFactorPending(boolean twoFactorPending) {
		this.twoFactorPending = twoFactorPending;
	}

	public String getIp() {
		return ip;
	}

	public void setIp(String ip) {
		this.ip = ip;
	}

	public String getUserAgent() {
		return userAgent;
	}

	public void setUserAgent(String userAgent) {
		this.userAgent = userAgent;
	}
}

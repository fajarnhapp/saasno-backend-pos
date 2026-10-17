package com.saasno.entity;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.saasno.utility.cryptography.Argon2;
import com.saasno.utility.generator.ID;
import com.saasno.utility.picker.UserStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.NamedQueries;
import jakarta.persistence.NamedQuery;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

@Entity
@Table(schema = "public", name = "user", indexes = {
        @Index(name = "user_username_idx", columnList = "username")
})
@NamedQueries({
        @NamedQuery(name = "UserEntity.findByUsername", query = "SELECT u FROM UserEntity u WHERE u.username = :username")
})
public class UserEntity {

    public static final PasswordEncoder passwordEncoder = Argon2.setDefault;

    public static final String DEFAULT_PASSWORD = "ff7bd97b1a7789ddd2775122fd6817f3173672da9f802ceec57f284325bf589f";

    @Id
    @Column(length = 64, nullable = false, updatable = false)
    private String id;

    @Column(length = 256, nullable = false)
    private String username;

    @Column(length = 256, nullable = false)
    private String password;

    @JsonIgnore
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "user_role", joinColumns = @JoinColumn(name = "user_id"), inverseJoinColumns = @JoinColumn(name = "role_id"))
    private Set<RoleEntity> roles = new HashSet<>();

    @Column(length = 32, nullable = false)
    @Enumerated(EnumType.STRING)
    private UserStatus status;

    @Column(nullable = false)
    private Boolean secure = false;

    @Column(nullable = false)
    private Boolean twoFactorAuth = false;

    @Column(length = 128)
    private String twoFactorAuthSecret;

    @Column(name = "branch_id", length = 64)
    private String branchId;

    @JsonIgnore
    @Column(name = "failed_login_count", nullable = false)
    private int failedLoginCount = 0;

    @JsonIgnore
    @Column(name = "locked_until")
    private LocalDateTime lockedUntil;

    @Column(nullable = false)
    private LocalDateTime modificated;

    @Column(nullable = false, updatable = false)
    private LocalDateTime registered;

    public UserEntity() {

    }

    public UserEntity(String username, Set<RoleEntity> role) {
        this.username = username;
        this.roles.addAll(role);
    }

    @PrePersist
    public void beforeInsert() {
        this.id = ID.uuid();
        this.password = passwordEncoder.encode(DEFAULT_PASSWORD);
        this.status = UserStatus.ACTIVED;
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

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public Set<RoleEntity> getRoles() {
        return roles;
    }

    public void setRoles(Set<RoleEntity> roles) {
        this.roles = roles;
    }

    public UserStatus getStatus() {
        return status;
    }

    public void setStatus(UserStatus status) {
        this.status = status;
    }

    public Boolean getSecure() {
        return secure;
    }

    public void setSecure(Boolean secure) {
        this.secure = secure;
    }

    public Boolean getTwoFactorAuth() {
        return twoFactorAuth;
    }

    public void setTwoFactorAuth(Boolean twoFactorAuth) {
        this.twoFactorAuth = twoFactorAuth;
    }

    public String getTwoFactorAuthSecret() {
        return twoFactorAuthSecret;
    }

    public void setTwoFactorAuthSecret(String twoFactorAuthSecret) {
        this.twoFactorAuthSecret = twoFactorAuthSecret;
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

    public boolean verifyPassword(String password) {
        return passwordEncoder.matches(password, this.password);
    }

    public UserEntity changePassword(String password) {
        this.password = passwordEncoder.encode(password);
        return this;
    }

    public UserEntity changePasswordDefault(String password) {
        this.password = passwordEncoder.encode(password);
        this.secure = true;
        return this;
    }

    public UserDetails details() {
        return new User(
                this.username,
                "SECRET",
                authorities());
    }

    public List<SimpleGrantedAuthority> authorities() {
        List<SimpleGrantedAuthority> authorities = new ArrayList<>();
        for (RoleEntity role : this.roles) {
            authorities.add(new SimpleGrantedAuthority("ROLE_" + role.getName()));
        }
        return authorities;
    }

    public List<String> roleNames() {
        List<String> list = new ArrayList<>();
        for (RoleEntity role : this.roles) {
            list.add(role.getName());
        }
        return list;
    }

    public String getBranchId() {
        return branchId;
    }

    public void setBranchId(String branchId) {
        this.branchId = branchId;
    }

    @JsonIgnore
    public int getFailedLoginCount() {
        return failedLoginCount;
    }

    @JsonIgnore
    public LocalDateTime getLockedUntil() {
        return lockedUntil;
    }

    @JsonIgnore
    public boolean isLocked() {
        return lockedUntil != null && lockedUntil.isAfter(LocalDateTime.now());
    }

    // Mencatat satu kegagalan; mengembalikan true bila akun baru saja terkunci. Kunci yang sudah lewat dihitung dari nol.
    public boolean recordFailedLogin(int maxAttempts, int lockMinutes) {
        if (lockedUntil != null && !isLocked()) {
            failedLoginCount = 0;
            lockedUntil = null;
        }
        failedLoginCount++;
        if (failedLoginCount >= maxAttempts) {
            lockedUntil = LocalDateTime.now().plusMinutes(lockMinutes);
            failedLoginCount = 0;
            return true;
        }
        return false;
    }

    public void clearFailedLogin() {
        failedLoginCount = 0;
        lockedUntil = null;
    }

}

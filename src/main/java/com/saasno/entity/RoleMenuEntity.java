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
@Table(name = "role_menu")
@NamedQueries({
        @NamedQuery(name = "RoleMenuEntity.findById", query = "SELECT rm FROM RoleMenuEntity rm WHERE rm.role.id = :roleId AND rm.menu.id = :menuId")
})
public class RoleMenuEntity {

    @EmbeddedId
    private RoleMenuId id = new RoleMenuId();

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("roleId")
    @JoinColumn(name = "role_id")
    private RoleEntity role;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("menuId")
    @JoinColumn(name = "menu_id")
    private MenuEntity menu;

    @Column(name = "registered")
    private LocalDateTime registered;

    @PrePersist
    public void beforeInsert() {
        this.id = new RoleMenuId(this.role.getId(), this.menu.getId());
        this.registered = LocalDateTime.now();
    }

    public RoleMenuId getId() {
        return id;
    }

    public void setId(RoleMenuId id) {
        this.id = id;
    }

    public RoleEntity getRole() {
        return role;
    }

    public void setRole(RoleEntity role) {
        this.role = role;
    }

    public MenuEntity getMenu() {
        return menu;
    }

    public void setMenu(MenuEntity menu) {
        this.menu = menu;
    }

    public LocalDateTime getRegistered() {
        return registered;
    }

    public void setRegistered(LocalDateTime registered) {
        this.registered = registered;
    }

}

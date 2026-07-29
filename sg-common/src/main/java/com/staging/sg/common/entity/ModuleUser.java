package com.staging.sg.common.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * Vue JPA minimale de la table users pour les modules réseau autonomes.
 *
 * <p>Elle évite d'importer dans une application DMAS les relations du portail
 * vers Test, MessageType et TpsStep. Le portail conserve l'entité User
 * complète dans son propre périmètre de persistance.</p>
 */
@Entity
@Table(name = "users")
public class ModuleUser {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false, length = 50)
    private String login;

    @Column(nullable = false, length = 255)
    private String password;

    @Column(nullable = false, length = 30)
    private String role;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "last_login")
    private LocalDateTime lastLogin;

    public Long getId() { return id; }
    public String getLogin() { return login; }
    public String getPassword() { return password; }
    public String getRole() { return role; }
    public boolean isActive() { return active; }
    public LocalDateTime getLastLogin() { return lastLogin; }

    public void setId(Long id) { this.id = id; }
    public void setLogin(String login) { this.login = login; }
    public void setPassword(String password) { this.password = password; }
    public void setRole(String role) { this.role = role; }
    public void setActive(boolean active) { this.active = active; }
    public void setLastLogin(LocalDateTime lastLogin) { this.lastLogin = lastLogin; }
}

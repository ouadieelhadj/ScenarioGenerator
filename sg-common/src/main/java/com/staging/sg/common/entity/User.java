package com.staging.sg.common.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false, length = 50)
    private String login;

    @Column(nullable = false, length = 255)
    private String password;

    @Column(length = 100)
    private String email;

    @Column(name = "role", nullable = false, length = 30)
    private String role;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "created_by", length = 50)
    private String createdBy;

    @Column(name = "last_login")
    private LocalDateTime lastLogin;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "user_tests",
        joinColumns = @JoinColumn(name = "user_id"),
        inverseJoinColumns = @JoinColumn(name = "test_id")
    )
    private Set<Test> assignedTests = new HashSet<>();

    public User() {}

    // Getters
    public Long          getId()           { return id; }
    public String        getLogin()        { return login; }
    public String        getPassword()     { return password; }
    public String        getEmail()        { return email; }
    public String        getRole()         { return role; }
    public boolean       isActive()        { return active; }
    public LocalDateTime getCreatedAt()    { return createdAt; }
    public String        getCreatedBy()    { return createdBy; }
    public LocalDateTime getLastLogin()    { return lastLogin; }
    public Set<Test>     getAssignedTests(){ return assignedTests; }

    // Setters
    public void setId(Long v)                  { this.id = v; }
    public void setLogin(String v)             { this.login = v; }
    public void setPassword(String v)          { this.password = v; }
    public void setEmail(String v)             { this.email = v; }
    public void setRole(String v)              { this.role = v; }
    public void setActive(boolean v)           { this.active = v; }
    public void setCreatedAt(LocalDateTime v)  { this.createdAt = v; }
    public void setCreatedBy(String v)         { this.createdBy = v; }
    public void setLastLogin(LocalDateTime v)  { this.lastLogin = v; }
    public void setAssignedTests(Set<Test> v)  { this.assignedTests = v; }
}

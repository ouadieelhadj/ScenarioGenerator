package com.staging.sg.common.entity;

import jakarta.persistence.*;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "roles")
public class Role {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 30)
    private String code;

    @Column(nullable = false, length = 100)
    private String label;

    @Column(length = 255)
    private String description;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
        name = "role_permissions",
        joinColumns = @JoinColumn(name = "role_id"),
        inverseJoinColumns = @JoinColumn(name = "permission_id")
    )
    private Set<Permission> permissions = new HashSet<>();

    public Role() {}

    public Long            getId()          { return id; }
    public String          getCode()        { return code; }
    public String          getLabel()       { return label; }
    public String          getDescription() { return description; }
    public Set<Permission> getPermissions() { return permissions; }

    public void setId(Long v)                    { this.id = v; }
    public void setCode(String v)                { this.code = v; }
    public void setLabel(String v)               { this.label = v; }
    public void setDescription(String v)         { this.description = v; }
    public void setPermissions(Set<Permission> v){ this.permissions = v; }
}

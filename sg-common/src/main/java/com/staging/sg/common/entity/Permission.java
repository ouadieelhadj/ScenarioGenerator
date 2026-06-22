package com.staging.sg.common.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "permissions")
public class Permission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String code;

    @Column(nullable = false, length = 100)
    private String label;

    @Column(nullable = false, length = 50)
    private String category;

    public Permission() {}

    public Long   getId()       { return id; }
    public String getCode()     { return code; }
    public String getLabel()    { return label; }
    public String getCategory() { return category; }

    public void setId(Long v)       { this.id = v; }
    public void setCode(String v)   { this.code = v; }
    public void setLabel(String v)  { this.label = v; }
    public void setCategory(String v){ this.category = v; }
}

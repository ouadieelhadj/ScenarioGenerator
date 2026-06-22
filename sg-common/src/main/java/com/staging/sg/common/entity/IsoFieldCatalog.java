package com.staging.sg.common.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "iso_field_catalog")
public class IsoFieldCatalog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "field_code", length = 10, nullable = false, unique = true) private String fieldCode;
    @Column(length = 60, nullable = false)   private String name;
    @Column(length = 255)                    private String description;
    @Column(name = "gen_strategy", length = 40, nullable = false) private String genStrategy;
    @Column(nullable = false)                private Boolean enabled = true;
    @Column(name = "display_order", nullable = false) private Integer displayOrder = 0;

    public IsoFieldCatalog() {}

    public Long getId()             { return id; }
    public String getFieldCode()    { return fieldCode; }
    public String getName()         { return name; }
    public String getDescription()  { return description; }
    public String getGenStrategy()  { return genStrategy; }
    public Boolean getEnabled()     { return enabled; }
    public Integer getDisplayOrder(){ return displayOrder; }

    public void setId(Long v)            { this.id = v; }
    public void setFieldCode(String v)   { this.fieldCode = v; }
    public void setName(String v)        { this.name = v; }
    public void setDescription(String v) { this.description = v; }
    public void setGenStrategy(String v) { this.genStrategy = v; }
    public void setEnabled(Boolean v)    { this.enabled = v; }
    public void setDisplayOrder(Integer v){ this.displayOrder = v; }
}

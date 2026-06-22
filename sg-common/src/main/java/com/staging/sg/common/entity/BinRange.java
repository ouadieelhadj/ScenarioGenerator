package com.staging.sg.common.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "bin_range")
public class BinRange {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 20, nullable = false)   private String code;
    @Column(name = "product_name", length = 60, nullable = false) private String productName;
    @Column(length = 20, nullable = false)   private String network = "MASTERCARD";
    @Column(name = "pan_length", nullable = false) private Integer panLength = 16;
    @Column(name = "is_range", nullable = false)   private Boolean isRange = false;
    @Column(nullable = false)                private Boolean enabled = true;

    public BinRange() {}

    public Long getId()            { return id; }
    public String getCode()        { return code; }
    public String getProductName() { return productName; }
    public String getNetwork()     { return network; }
    public Integer getPanLength()  { return panLength; }
    public Boolean getIsRange()    { return isRange; }
    public Boolean getEnabled()    { return enabled; }

    public void setId(Long v)            { this.id = v; }
    public void setCode(String v)        { this.code = v; }
    public void setProductName(String v) { this.productName = v; }
    public void setNetwork(String v)     { this.network = v; }
    public void setPanLength(Integer v)  { this.panLength = v; }
    public void setIsRange(Boolean v)    { this.isRange = v; }
    public void setEnabled(Boolean v)    { this.enabled = v; }
}

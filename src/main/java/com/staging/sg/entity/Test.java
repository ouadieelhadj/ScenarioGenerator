package com.staging.sg.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Entity
@Table(name = "tests")
public class Test {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 255)
    private String description;

    @Column(length = 50)
    private String category;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "message_type_id")
    private MessageType messageType;

    @Column(columnDefinition = "TEXT")
    private String config;

    @Column(name = "expected_de039", length = 2)
    private String expectedDe039;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private User createdBy;

    @OneToMany(mappedBy = "test", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("stepOrder ASC")
    private List<TpsStep> tpsSteps = new ArrayList<>();

    // Relation inverse de User.assignedTests
    @ManyToMany(mappedBy = "assignedTests", fetch = FetchType.LAZY)
    private Set<User> assignedUsers = new HashSet<>();

    public Test() {}

    // Getters
    public Long          getId()            { return id; }
    public String        getName()          { return name; }
    public String        getDescription()   { return description; }
    public String        getCategory()      { return category; }
    public MessageType   getMessageType()   { return messageType; }
    public String        getConfig()        { return config; }
    public String        getExpectedDe039() { return expectedDe039; }
    public boolean       isActive()         { return active; }
    public LocalDateTime getCreatedAt()     { return createdAt; }
    public User          getCreatedBy()     { return createdBy; }
    public List<TpsStep> getTpsSteps()      { return tpsSteps; }
    public Set<User>     getAssignedUsers() { return assignedUsers; }

    // Setters
    public void setId(Long v)                  { this.id = v; }
    public void setName(String v)              { this.name = v; }
    public void setDescription(String v)       { this.description = v; }
    public void setCategory(String v)          { this.category = v; }
    public void setMessageType(MessageType v)  { this.messageType = v; }
    public void setConfig(String v)            { this.config = v; }
    public void setExpectedDe039(String v)     { this.expectedDe039 = v; }
    public void setActive(boolean v)           { this.active = v; }
    public void setCreatedAt(LocalDateTime v)  { this.createdAt = v; }
    public void setCreatedBy(User v)           { this.createdBy = v; }
    public void setTpsSteps(List<TpsStep> v)   { this.tpsSteps = v; }
    public void setAssignedUsers(Set<User> v)  { this.assignedUsers = v; }
}

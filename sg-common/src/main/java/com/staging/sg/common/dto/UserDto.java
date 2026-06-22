package com.staging.sg.common.dto;


public class UserDto {
    private Long    id;
    private String  login;
    private String  email;
    private String  role;
    private boolean active;

    public Long    getId()     { return id; }
    public String  getLogin()  { return login; }
    public String  getEmail()  { return email; }
    public String  getRole()   { return role; }
    public boolean isActive()  { return active; }

    public void setId(Long v)      { this.id = v; }
    public void setLogin(String v) { this.login = v; }
    public void setEmail(String v) { this.email = v; }
    public void setRole(String v)  { this.role = v; }
    public void setActive(boolean v){ this.active = v; }
}

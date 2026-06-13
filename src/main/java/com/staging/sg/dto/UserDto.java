package com.staging.sg.dto;

import com.staging.sg.entity.Role;

public class UserDto {
    private Long    id;
    private String  login;
    private String  email;
    private Role    role;
    private boolean active;

    public Long    getId()     { return id; }
    public String  getLogin()  { return login; }
    public String  getEmail()  { return email; }
    public Role    getRole()   { return role; }
    public boolean isActive()  { return active; }

    public void setId(Long v)      { this.id = v; }
    public void setLogin(String v) { this.login = v; }
    public void setEmail(String v) { this.email = v; }
    public void setRole(Role v)    { this.role = v; }
    public void setActive(boolean v){ this.active = v; }
}

package com.staging.sg.dto;

import com.staging.sg.entity.Role;

public class CreateUserRequest {
    private String login;
    private String password;
    private String email;
    private Role   role;

    public String getLogin()    { return login; }
    public String getPassword() { return password; }
    public String getEmail()    { return email; }
    public Role   getRole()     { return role; }

    public void setLogin(String v)    { this.login = v; }
    public void setPassword(String v) { this.password = v; }
    public void setEmail(String v)    { this.email = v; }
    public void setRole(Role v)       { this.role = v; }
}

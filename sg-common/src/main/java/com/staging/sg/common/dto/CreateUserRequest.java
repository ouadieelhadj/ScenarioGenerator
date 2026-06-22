package com.staging.sg.common.dto;


public class CreateUserRequest {
    private String login;
    private String password;
    private String email;
    private String role;

    public String getLogin()    { return login; }
    public String getPassword() { return password; }
    public String getEmail()    { return email; }
    public String getRole()   { return role; }

    public void setLogin(String v)    { this.login = v; }
    public void setPassword(String v) { this.password = v; }
    public void setEmail(String v)    { this.email = v; }
    public void setRole(String v) { this.role = v; }
}

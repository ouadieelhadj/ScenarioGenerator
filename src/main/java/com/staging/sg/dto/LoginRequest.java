package com.staging.sg.dto;

public class LoginRequest {
    private String login;
    private String password;
    public String getLogin()    { return login; }
    public String getPassword() { return password; }
    public void setLogin(String v)    { this.login = v; }
    public void setPassword(String v) { this.password = v; }
}

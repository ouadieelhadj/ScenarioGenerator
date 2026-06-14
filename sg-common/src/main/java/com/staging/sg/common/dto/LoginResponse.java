package com.staging.sg.common.dto;

public class LoginResponse {
    private String token;
    private String login;
    private String role;
    private long   expiresIn;

    public LoginResponse(String token, String login, String role, long expiresIn) {
        this.token    = token;
        this.login    = login;
        this.role     = role;
        this.expiresIn = expiresIn;
    }

    public String getToken()    { return token; }
    public String getLogin()    { return login; }
    public String getRole()     { return role; }
    public long   getExpiresIn(){ return expiresIn; }
}

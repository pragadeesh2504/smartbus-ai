package com.smartbus.driver.api.model;

public class LoginResponse {
    private String accessToken;
    private String refreshToken;
    private String email;
    private String role;
    private String name;

    public String getAccessToken() { return accessToken; }
    public String getRefreshToken() { return refreshToken; }
    public String getEmail() { return email; }
    public String getRole() { return role; }
    public String getName() { return name; }
}

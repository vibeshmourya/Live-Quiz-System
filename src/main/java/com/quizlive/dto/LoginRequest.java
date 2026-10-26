package com.quizlive.dto;

import jakarta.validation.constraints.NotBlank;

public class LoginRequest {

    private String username;
    private String email;

    @NotBlank(message = "Password is required")
    private String password;

    public LoginRequest() {}

    public LoginRequest(String username, String password) {
        this.username = username;
        this.password = password;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getIdentifier() {
        if (email != null && !email.trim().isEmpty()) {
            return email.trim();
        }
        return username != null ? username.trim() : "";
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}

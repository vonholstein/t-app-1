package com.userservice.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public class CreateUserRequest {
    @JsonProperty("username")
    private String username;

    @JsonProperty("role")
    private String role;

    @JsonProperty("password")
    private String password;

    public CreateUserRequest() {
    }

    public CreateUserRequest(String username, String role) {
        this.username = username;
        this.role = role;
    }

    public CreateUserRequest(String username, String role, String password) {
        this.username = username;
        this.role = role;
        this.password = password;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public void validate() throws IllegalArgumentException {
        if (username == null || username.trim().isEmpty()) {
            throw new IllegalArgumentException("Username is required and cannot be empty");
        }
        if (role == null || role.trim().isEmpty()) {
            throw new IllegalArgumentException("Role is required and cannot be empty");
        }
        if (password == null || password.length() < 8) {
            throw new IllegalArgumentException("Password is required and must be at least 8 characters");
        }
        // Validate that role is one of the allowed values
        UserRole.fromString(role);
    }

    @Override
    public String toString() {
        return "CreateUserRequest{" +
                "username='" + username + '\'' +
                ", role='" + role + '\'' +
                ", password='[REDACTED]'" +
                '}';
    }
}

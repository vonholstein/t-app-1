package com.userservice.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public class CreateUserRequest {
    @JsonProperty("username")
    private String username;

    @JsonProperty("role")
    private String role;

    public CreateUserRequest() {
    }

    public CreateUserRequest(String username, String role) {
        this.username = username;
        this.role = role;
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

    public void validate() throws IllegalArgumentException {
        if (username == null || username.trim().isEmpty()) {
            throw new IllegalArgumentException("Username is required and cannot be empty");
        }
        if (role == null || role.trim().isEmpty()) {
            throw new IllegalArgumentException("Role is required and cannot be empty");
        }
        // Validate that role is one of the allowed values
        UserRole.fromString(role);
    }

    @Override
    public String toString() {
        return "CreateUserRequest{" +
                "username='" + username + '\'' +
                ", role='" + role + '\'' +
                '}';
    }
}

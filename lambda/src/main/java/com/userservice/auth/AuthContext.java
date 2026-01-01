package com.userservice.auth;

import com.userservice.model.UserRole;

public class AuthContext {
    private final String username;
    private final String cognitoSub;
    private final UserRole role;

    public AuthContext(String username, String cognitoSub, UserRole role) {
        this.username = username;
        this.cognitoSub = cognitoSub;
        this.role = role;
    }

    public String getUsername() {
        return username;
    }

    public String getCognitoSub() {
        return cognitoSub;
    }

    public UserRole getRole() {
        return role;
    }

    public boolean isGuest() {
        return role == UserRole.GUEST;
    }

    public boolean isUser() {
        return role == UserRole.USER;
    }

    public boolean isSuperUser() {
        return role == UserRole.SUPERUSER;
    }

    public boolean isGlobalAdmin() {
        return role == UserRole.GLOBALADMIN;
    }

    @Override
    public String toString() {
        return "AuthContext{" +
                "username='" + username + '\'' +
                ", cognitoSub='" + cognitoSub + '\'' +
                ", role=" + role +
                '}';
    }
}

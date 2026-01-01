package com.userservice.model;

public enum UserRole {
    GUEST("guest"),
    USER("user"),
    SUPERUSER("superuser"),
    GLOBALADMIN("globaladmin");

    private final String value;

    UserRole(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static UserRole fromString(String text) {
        for (UserRole role : UserRole.values()) {
            if (role.value.equalsIgnoreCase(text)) {
                return role;
            }
        }
        throw new IllegalArgumentException("Invalid user role: " + text +
            ". Valid values are: guest, user, superuser, globaladmin");
    }

    @Override
    public String toString() {
        return value;
    }
}

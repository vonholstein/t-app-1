package com.userservice.auth;

public class ForbiddenException extends Exception {
    public ForbiddenException(String message) {
        super(message);
    }
}

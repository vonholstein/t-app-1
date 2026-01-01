package com.userservice.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Base64;

public class CognitoTokenValidator {
    private static final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Extract username (cognito:username claim) from JWT token
     * API Gateway validates the JWT signature, so we only need to parse claims
     */
    public static String extractUsername(String jwtToken) throws Exception {
        String[] parts = jwtToken.split("\\.");
        if (parts.length != 3) {
            throw new IllegalArgumentException("Invalid JWT token format");
        }

        String payload = new String(Base64.getUrlDecoder().decode(parts[1]));
        JsonNode claims = objectMapper.readTree(payload);

        // Extract username from 'cognito:username' claim
        JsonNode usernameNode = claims.get("cognito:username");
        if (usernameNode == null) {
            throw new IllegalArgumentException("Username claim not found in JWT token");
        }

        return usernameNode.asText();
    }

    /**
     * Extract role from JWT custom attributes (custom:role claim)
     */
    public static String extractRole(String jwtToken) throws Exception {
        String[] parts = jwtToken.split("\\.");
        if (parts.length != 3) {
            throw new IllegalArgumentException("Invalid JWT token format");
        }

        String payload = new String(Base64.getUrlDecoder().decode(parts[1]));
        JsonNode claims = objectMapper.readTree(payload);

        // Extract role from custom:role claim
        JsonNode roleNode = claims.get("custom:role");
        if (roleNode == null) {
            throw new IllegalArgumentException("Role claim not found in JWT token");
        }

        return roleNode.asText();
    }

    /**
     * Extract Cognito user sub (unique identifier) from JWT token
     */
    public static String extractSub(String jwtToken) throws Exception {
        String[] parts = jwtToken.split("\\.");
        if (parts.length != 3) {
            throw new IllegalArgumentException("Invalid JWT token format");
        }

        String payload = new String(Base64.getUrlDecoder().decode(parts[1]));
        JsonNode claims = objectMapper.readTree(payload);

        JsonNode subNode = claims.get("sub");
        if (subNode == null) {
            throw new IllegalArgumentException("Sub claim not found in JWT token");
        }

        return subNode.asText();
    }
}

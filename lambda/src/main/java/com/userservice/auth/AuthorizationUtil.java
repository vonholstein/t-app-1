package com.userservice.auth;

import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.userservice.model.UserRole;
import java.util.Map;

public class AuthorizationUtil {

    /**
     * Extract AuthContext from API Gateway request
     * Returns null if authorization header is missing (for public endpoints)
     */
    public static AuthContext extractAuthContext(APIGatewayProxyRequestEvent event)
            throws UnauthorizedException {
        Map<String, String> headers = event.getHeaders();
        if (headers == null || !headers.containsKey("Authorization")) {
            return null; // Public endpoint - no auth header
        }

        String authHeader = headers.get("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new UnauthorizedException("Invalid Authorization header format. Expected 'Bearer <token>'");
        }

        String jwtToken = authHeader.substring(7); // Remove "Bearer " prefix

        try {
            String username = CognitoTokenValidator.extractUsername(jwtToken);
            String sub = CognitoTokenValidator.extractSub(jwtToken);
            String roleString = CognitoTokenValidator.extractRole(jwtToken);

            UserRole role = UserRole.fromString(roleString);

            return new AuthContext(username, sub, role);
        } catch (Exception e) {
            throw new UnauthorizedException("Invalid or malformed JWT token: " + e.getMessage());
        }
    }

    /**
     * Check if user can read users list
     * All authenticated users can read (guest, user, superuser, globaladmin)
     */
    public static boolean canReadUsers(AuthContext authContext) {
        return authContext != null; // All authenticated users can read
    }

    /**
     * Check if user can read a specific user
     * All authenticated users can read any user (guest, user, superuser, globaladmin)
     */
    public static boolean canReadUser(AuthContext authContext, String targetUserId) {
        return authContext != null; // All authenticated users can read any user
    }

    /**
     * Check if user can create a user entry
     * - guest: Cannot create
     * - user: Can create (registration flow, but only their own)
     * - superuser: Can create
     * - globaladmin: Can create
     * - null (unauthenticated): Can create (public registration)
     */
    public static boolean canCreateUser(AuthContext authContext) {
        if (authContext == null) {
            return true; // Public registration endpoint
        }

        return !authContext.isGuest(); // All except guest
    }

    /**
     * Check if authenticated user trying to create can create this specific username
     * - Unauthenticated: Can create any username (public registration)
     * - User role: Can only create their own username
     * - Superuser/Globaladmin: Can create any username
     */
    public static boolean canCreateUsername(AuthContext authContext, String targetUsername) {
        if (authContext == null) {
            return true; // Public registration - can create any username
        }

        if (authContext.isUser()) {
            // User role can only create their own entry
            return authContext.getUsername().equals(targetUsername);
        }

        // Superuser and globaladmin can create any username
        return authContext.isSuperUser() || authContext.isGlobalAdmin();
    }

    /**
     * Check if user can delete a specific user
     * - guest: Cannot delete
     * - user: Cannot delete
     * - superuser: Can delete only their own user entry
     * - globaladmin: Can delete any user
     */
    public static boolean canDeleteUser(AuthContext authContext, String targetUsername) {
        if (authContext == null) {
            return false;
        }

        if (authContext.isGlobalAdmin()) {
            return true; // Global admin can delete anyone
        }

        if (authContext.isSuperUser()) {
            // Superuser can only delete themselves
            return authContext.getUsername().equals(targetUsername);
        }

        return false; // guest and user cannot delete
    }

    /**
     * Get descriptive authorization error message
     */
    public static String getUnauthorizedMessage(String operation) {
        return "You are not authorized to " + operation;
    }
}

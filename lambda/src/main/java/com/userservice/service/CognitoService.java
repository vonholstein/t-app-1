package com.userservice.service;

import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.cognitoidentityprovider.model.*;

public class CognitoService {
    private final CognitoIdentityProviderClient cognitoClient;
    private final String userPoolId;

    public CognitoService() {
        this.cognitoClient = CognitoIdentityProviderClient.builder().build();
        this.userPoolId = System.getenv("USER_POOL_ID");
    }

    /**
     * Create Cognito user with username, role, and password
     * Sets password as permanent (no temporary password flow)
     */
    public void createUser(String username, String role, String password) throws Exception {
        // Set custom role attribute
        AttributeType roleAttribute = AttributeType.builder()
                .name("custom:role")
                .value(role)
                .build();

        try {
            // Create user with temporary password
            AdminCreateUserRequest createRequest = AdminCreateUserRequest.builder()
                    .userPoolId(userPoolId)
                    .username(username)
                    .temporaryPassword(password)
                    .userAttributes(roleAttribute)
                    .messageAction(MessageActionType.SUPPRESS) // Don't send welcome email
                    .build();

            cognitoClient.adminCreateUser(createRequest);

            // Set permanent password (skip temporary password flow for simplicity)
            AdminSetUserPasswordRequest setPasswordRequest =
                    AdminSetUserPasswordRequest.builder()
                            .userPoolId(userPoolId)
                            .username(username)
                            .password(password)
                            .permanent(true)
                            .build();

            cognitoClient.adminSetUserPassword(setPasswordRequest);

        } catch (UsernameExistsException e) {
            throw new Exception("Username already exists in Cognito: " + username);
        } catch (InvalidPasswordException e) {
            throw new Exception("Password does not meet Cognito password policy: " + e.getMessage());
        } catch (CognitoIdentityProviderException e) {
            throw new Exception("Error creating Cognito user: " + e.getMessage());
        }
    }

    /**
     * Delete Cognito user by username
     * Silently succeeds if user doesn't exist
     */
    public void deleteUser(String username) throws Exception {
        AdminDeleteUserRequest request = AdminDeleteUserRequest.builder()
                .userPoolId(userPoolId)
                .username(username)
                .build();

        try {
            cognitoClient.adminDeleteUser(request);
        } catch (UserNotFoundException e) {
            // User doesn't exist in Cognito - that's okay, no error
        } catch (CognitoIdentityProviderException e) {
            throw new Exception("Error deleting Cognito user: " + e.getMessage());
        }
    }

    /**
     * Update user role in Cognito custom attributes
     */
    public void updateUserRole(String username, String newRole) throws Exception {
        AttributeType roleAttribute = AttributeType.builder()
                .name("custom:role")
                .value(newRole)
                .build();

        AdminUpdateUserAttributesRequest request =
                AdminUpdateUserAttributesRequest.builder()
                        .userPoolId(userPoolId)
                        .username(username)
                        .userAttributes(roleAttribute)
                        .build();

        try {
            cognitoClient.adminUpdateUserAttributes(request);
        } catch (UserNotFoundException e) {
            throw new Exception("User not found in Cognito: " + username);
        } catch (CognitoIdentityProviderException e) {
            throw new Exception("Error updating user role: " + e.getMessage());
        }
    }
}

package com.userservice.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.userservice.auth.AuthContext;
import com.userservice.auth.AuthorizationUtil;
import com.userservice.auth.UnauthorizedException;
import com.userservice.service.CognitoService;
import com.userservice.util.ResponseUtil;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.util.HashMap;
import java.util.Map;

public class DeleteUserHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {
    private final DynamoDbClient dynamoDb;
    private final String tableName;
    private final CognitoService cognitoService;

    public DeleteUserHandler() {
        this.dynamoDb = DynamoDbClient.builder().build();
        this.tableName = System.getenv("TABLE_NAME");
        this.cognitoService = new CognitoService();
    }

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent event, Context context) {
        context.getLogger().log("DeleteUserHandler - Request received");

        try {
            // Extract auth context (REQUIRED for this endpoint)
            AuthContext authContext;
            try {
                authContext = AuthorizationUtil.extractAuthContext(event);
                if (authContext == null) {
                    return ResponseUtil.unauthorized("Authentication required");
                }
            } catch (UnauthorizedException e) {
                return ResponseUtil.unauthorized(e.getMessage());
            }

            // Get userId from path parameters
            Map<String, String> pathParameters = event.getPathParameters();
            if (pathParameters == null || !pathParameters.containsKey("userId")) {
                return ResponseUtil.badRequest("userId is required in path");
            }

            String userId = pathParameters.get("userId");
            if (userId == null || userId.trim().isEmpty()) {
                return ResponseUtil.badRequest("userId cannot be empty");
            }

            context.getLogger().log("Deleting user: " + userId);

            // First get user from DynamoDB to retrieve username for authorization check
            GetItemRequest getItemRequest = GetItemRequest.builder()
                    .tableName(tableName)
                    .key(Map.of("userId", AttributeValue.builder().s(userId).build()))
                    .build();

            GetItemResponse getResponse = dynamoDb.getItem(getItemRequest);

            if (!getResponse.hasItem() || getResponse.item().isEmpty()) {
                return ResponseUtil.notFound("User not found with userId: " + userId);
            }

            String targetUsername = getResponse.item().get("username").s();

            // Check authorization (role + ownership check)
            if (!AuthorizationUtil.canDeleteUser(authContext, targetUsername)) {
                return ResponseUtil.forbidden(
                        AuthorizationUtil.getUnauthorizedMessage("delete this user")
                );
            }

            // Delete from Cognito FIRST
            try {
                cognitoService.deleteUser(targetUsername);
                context.getLogger().log("Cognito user deleted: " + targetUsername);
            } catch (Exception e) {
                context.getLogger().log("Error deleting Cognito user: " + e.getMessage());
                // Continue with DynamoDB deletion even if Cognito fails
            }

            // Delete from DynamoDB
            DeleteItemRequest deleteItemRequest = DeleteItemRequest.builder()
                    .tableName(tableName)
                    .key(Map.of("userId", AttributeValue.builder().s(userId).build()))
                    .build();

            dynamoDb.deleteItem(deleteItemRequest);

            // Create success response
            Map<String, String> responseData = new HashMap<>();
            responseData.put("message", "User deleted successfully");
            responseData.put("userId", userId);

            context.getLogger().log("User deleted successfully: " + userId);
            return ResponseUtil.success(responseData);

        } catch (Exception e) {
            context.getLogger().log("Error deleting user: " + e.getMessage());
            e.printStackTrace();
            return ResponseUtil.internalServerError("Error deleting user: " + e.getMessage());
        }
    }
}

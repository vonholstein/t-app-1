package com.userservice.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.userservice.auth.AuthContext;
import com.userservice.auth.AuthorizationUtil;
import com.userservice.auth.UnauthorizedException;
import com.userservice.model.User;
import com.userservice.util.ResponseUtil;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemResponse;

import java.util.Map;

public class GetUserHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {
    private final DynamoDbClient dynamoDb;
    private final String tableName;

    public GetUserHandler() {
        this.dynamoDb = DynamoDbClient.builder().build();
        this.tableName = System.getenv("TABLE_NAME");
    }

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent event, Context context) {
        context.getLogger().log("GetUserHandler - Request received");

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

            // Check read permission (all authenticated users can read)
            if (!AuthorizationUtil.canReadUser(authContext, userId)) {
                return ResponseUtil.forbidden("You are not authorized to view users");
            }

            context.getLogger().log("Getting user: " + userId);

            // Get item from DynamoDB
            GetItemRequest getItemRequest = GetItemRequest.builder()
                    .tableName(tableName)
                    .key(Map.of("userId", AttributeValue.builder().s(userId).build()))
                    .build();

            GetItemResponse response = dynamoDb.getItem(getItemRequest);

            // Check if item exists
            if (!response.hasItem() || response.item().isEmpty()) {
                return ResponseUtil.notFound("User not found with userId: " + userId);
            }

            // Parse item to User object
            Map<String, AttributeValue> item = response.item();
            User user = new User(
                    item.get("userId").s(),
                    item.get("username").s(),
                    item.get("role").s(),
                    Long.parseLong(item.get("createdAt").n()),
                    Long.parseLong(item.get("updatedAt").n())
            );

            context.getLogger().log("User found: " + userId);
            return ResponseUtil.success(user);

        } catch (Exception e) {
            context.getLogger().log("Error getting user: " + e.getMessage());
            e.printStackTrace();
            return ResponseUtil.internalServerError("Error getting user: " + e.getMessage());
        }
    }
}

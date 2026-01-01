package com.userservice.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.userservice.util.ResponseUtil;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.util.HashMap;
import java.util.Map;

public class DeleteUserHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {
    private final DynamoDbClient dynamoDb;
    private final String tableName;

    public DeleteUserHandler() {
        this.dynamoDb = DynamoDbClient.builder().build();
        this.tableName = System.getenv("TABLE_NAME");
    }

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent event, Context context) {
        context.getLogger().log("DeleteUserHandler - Request received");

        try {
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

            // First check if user exists
            GetItemRequest getItemRequest = GetItemRequest.builder()
                    .tableName(tableName)
                    .key(Map.of("userId", AttributeValue.builder().s(userId).build()))
                    .build();

            GetItemResponse getResponse = dynamoDb.getItem(getItemRequest);

            if (!getResponse.hasItem() || getResponse.item().isEmpty()) {
                return ResponseUtil.notFound("User not found with userId: " + userId);
            }

            // Delete item from DynamoDB
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

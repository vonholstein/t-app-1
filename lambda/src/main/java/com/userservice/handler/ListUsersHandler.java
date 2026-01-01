package com.userservice.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.userservice.model.User;
import com.userservice.util.ResponseUtil;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ScanRequest;
import software.amazon.awssdk.services.dynamodb.model.ScanResponse;

import java.util.*;

public class ListUsersHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {
    private final DynamoDbClient dynamoDb;
    private final String tableName;
    private static final int DEFAULT_LIMIT = 20;

    public ListUsersHandler() {
        this.dynamoDb = DynamoDbClient.builder().build();
        this.tableName = System.getenv("TABLE_NAME");
    }

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent event, Context context) {
        context.getLogger().log("ListUsersHandler - Request received");

        try {
            // Get pagination parameters from query string
            Map<String, String> queryParams = event.getQueryStringParameters();
            int limit = DEFAULT_LIMIT;
            Map<String, AttributeValue> exclusiveStartKey = null;

            if (queryParams != null) {
                // Parse limit
                if (queryParams.containsKey("limit")) {
                    try {
                        limit = Integer.parseInt(queryParams.get("limit"));
                        if (limit <= 0 || limit > 100) {
                            return ResponseUtil.badRequest("Limit must be between 1 and 100");
                        }
                    } catch (NumberFormatException e) {
                        return ResponseUtil.badRequest("Invalid limit parameter");
                    }
                }

                // Parse lastEvaluatedKey (simple implementation - in production you'd want to encode this)
                if (queryParams.containsKey("lastEvaluatedKey")) {
                    String lastKey = queryParams.get("lastEvaluatedKey");
                    exclusiveStartKey = new HashMap<>();
                    exclusiveStartKey.put("userId", AttributeValue.builder().s(lastKey).build());
                }
            }

            // Build scan request
            ScanRequest.Builder scanBuilder = ScanRequest.builder()
                    .tableName(tableName)
                    .limit(limit);

            if (exclusiveStartKey != null) {
                scanBuilder.exclusiveStartKey(exclusiveStartKey);
            }

            ScanResponse scanResponse = dynamoDb.scan(scanBuilder.build());

            // Convert items to User objects
            List<User> users = new ArrayList<>();
            for (Map<String, AttributeValue> item : scanResponse.items()) {
                User user = new User(
                        item.get("userId").s(),
                        item.get("username").s(),
                        item.get("role").s(),
                        Long.parseLong(item.get("createdAt").n()),
                        Long.parseLong(item.get("updatedAt").n())
                );
                users.add(user);
            }

            // Build response
            Map<String, Object> responseData = new HashMap<>();
            responseData.put("users", users);
            responseData.put("count", users.size());

            // Add pagination token if there are more results
            if (scanResponse.hasLastEvaluatedKey() && !scanResponse.lastEvaluatedKey().isEmpty()) {
                String lastEvaluatedKey = scanResponse.lastEvaluatedKey().get("userId").s();
                responseData.put("lastEvaluatedKey", lastEvaluatedKey);
            }

            context.getLogger().log("Listed " + users.size() + " users");
            return ResponseUtil.success(responseData);

        } catch (Exception e) {
            context.getLogger().log("Error listing users: " + e.getMessage());
            e.printStackTrace();
            return ResponseUtil.internalServerError("Error listing users: " + e.getMessage());
        }
    }
}

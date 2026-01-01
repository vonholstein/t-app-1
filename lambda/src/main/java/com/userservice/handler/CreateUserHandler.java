package com.userservice.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.userservice.model.CreateUserRequest;
import com.userservice.model.User;
import com.userservice.util.ResponseUtil;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class CreateUserHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {
    private final DynamoDbClient dynamoDb;
    private final String tableName;
    private final ObjectMapper objectMapper;

    public CreateUserHandler() {
        this.dynamoDb = DynamoDbClient.builder().build();
        this.tableName = System.getenv("TABLE_NAME");
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent event, Context context) {
        context.getLogger().log("CreateUserHandler - Request received");

        try {
            // Parse request body
            String body = event.getBody();
            if (body == null || body.trim().isEmpty()) {
                return ResponseUtil.badRequest("Request body is required");
            }

            CreateUserRequest request = objectMapper.readValue(body, CreateUserRequest.class);

            // Validate input
            try {
                request.validate();
            } catch (IllegalArgumentException e) {
                return ResponseUtil.badRequest(e.getMessage());
            }

            // Check if username already exists
            if (usernameExists(request.getUsername())) {
                return ResponseUtil.conflict("Username '" + request.getUsername() + "' already exists");
            }

            // Generate UUID for userId
            String userId = UUID.randomUUID().toString();
            long currentTime = System.currentTimeMillis();

            // Create user item
            Map<String, AttributeValue> item = new HashMap<>();
            item.put("userId", AttributeValue.builder().s(userId).build());
            item.put("username", AttributeValue.builder().s(request.getUsername()).build());
            item.put("role", AttributeValue.builder().s(request.getRole()).build());
            item.put("createdAt", AttributeValue.builder().n(String.valueOf(currentTime)).build());
            item.put("updatedAt", AttributeValue.builder().n(String.valueOf(currentTime)).build());

            // Put item in DynamoDB
            PutItemRequest putItemRequest = PutItemRequest.builder()
                    .tableName(tableName)
                    .item(item)
                    .build();

            dynamoDb.putItem(putItemRequest);

            // Create response
            User user = new User(userId, request.getUsername(), request.getRole(), currentTime, currentTime);

            context.getLogger().log("User created successfully: " + userId);
            return ResponseUtil.created(user);

        } catch (Exception e) {
            context.getLogger().log("Error creating user: " + e.getMessage());
            e.printStackTrace();
            return ResponseUtil.internalServerError("Error creating user: " + e.getMessage());
        }
    }

    private boolean usernameExists(String username) {
        try {
            QueryRequest queryRequest = QueryRequest.builder()
                    .tableName(tableName)
                    .indexName("username-index")
                    .keyConditionExpression("username = :username")
                    .expressionAttributeValues(Map.of(
                            ":username", AttributeValue.builder().s(username).build()
                    ))
                    .limit(1)
                    .build();

            QueryResponse queryResponse = dynamoDb.query(queryRequest);
            return !queryResponse.items().isEmpty();
        } catch (Exception e) {
            throw new RuntimeException("Error checking username uniqueness", e);
        }
    }
}

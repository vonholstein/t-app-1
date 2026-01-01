package com.userservice.util;

import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.HashMap;
import java.util.Map;

public class ResponseUtil {
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private static final Map<String, String> DEFAULT_HEADERS = new HashMap<>();

    static {
        DEFAULT_HEADERS.put("Content-Type", "application/json");
        DEFAULT_HEADERS.put("Access-Control-Allow-Origin", "*");
        DEFAULT_HEADERS.put("Access-Control-Allow-Methods", "GET, POST, DELETE, OPTIONS");
        DEFAULT_HEADERS.put("Access-Control-Allow-Headers", "Content-Type,X-Amz-Date,Authorization,X-Api-Key,X-Amz-Security-Token");
    }

    public static APIGatewayProxyResponseEvent success(Object data) {
        return response(200, data);
    }

    public static APIGatewayProxyResponseEvent created(Object data) {
        return response(201, data);
    }

    public static APIGatewayProxyResponseEvent noContent() {
        return new APIGatewayProxyResponseEvent()
                .withStatusCode(204)
                .withHeaders(DEFAULT_HEADERS);
    }

    public static APIGatewayProxyResponseEvent badRequest(String message) {
        return error(400, message);
    }

    public static APIGatewayProxyResponseEvent notFound(String message) {
        return error(404, message);
    }

    public static APIGatewayProxyResponseEvent conflict(String message) {
        return error(409, message);
    }

    public static APIGatewayProxyResponseEvent internalServerError(String message) {
        return error(500, message);
    }

    private static APIGatewayProxyResponseEvent response(int statusCode, Object data) {
        try {
            String body = objectMapper.writeValueAsString(data);
            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(statusCode)
                    .withHeaders(DEFAULT_HEADERS)
                    .withBody(body);
        } catch (JsonProcessingException e) {
            return internalServerError("Error serializing response: " + e.getMessage());
        }
    }

    private static APIGatewayProxyResponseEvent error(int statusCode, String message) {
        Map<String, String> errorBody = new HashMap<>();
        errorBody.put("error", message);
        try {
            String body = objectMapper.writeValueAsString(errorBody);
            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(statusCode)
                    .withHeaders(DEFAULT_HEADERS)
                    .withBody(body);
        } catch (JsonProcessingException e) {
            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(500)
                    .withHeaders(DEFAULT_HEADERS)
                    .withBody("{\"error\":\"Internal server error\"}");
        }
    }
}

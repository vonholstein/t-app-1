package com.userservice.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.userservice.auth.AuthContext;
import com.userservice.auth.AuthorizationUtil;
import com.userservice.auth.UnauthorizedException;
import com.userservice.util.ResponseUtil;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

public class UploadFileHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private final S3Client s3Client;
    private final String bucketName;

    // Maximum file size: 10MB (API Gateway limit)
    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024;

    public UploadFileHandler() {
        this.s3Client = S3Client.builder().build();
        this.bucketName = System.getenv("BUCKET_NAME");
    }

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent event, Context context) {
        context.getLogger().log("UploadFileHandler - Request received");

        try {
            // AUTHENTICATION: Extract auth context (REQUIRED)
            AuthContext authContext;
            try {
                authContext = AuthorizationUtil.extractAuthContext(event);
                if (authContext == null) {
                    return ResponseUtil.unauthorized("Authentication required to upload files");
                }
            } catch (UnauthorizedException e) {
                return ResponseUtil.unauthorized(e.getMessage());
            }

            // AUTHORIZATION: Check if user can upload files
            if (!canUploadFile(authContext)) {
                return ResponseUtil.forbidden("You are not authorized to upload files");
            }

            // EXTRACT FILENAME from path parameters
            Map<String, String> pathParameters = event.getPathParameters();
            if (pathParameters == null || !pathParameters.containsKey("filename")) {
                return ResponseUtil.badRequest("filename is required in path");
            }

            String filename = pathParameters.get("filename");
            if (filename == null || filename.trim().isEmpty()) {
                return ResponseUtil.badRequest("filename cannot be empty");
            }

            // VALIDATE FILENAME (security: prevent path traversal)
            if (!isValidFilename(filename)) {
                return ResponseUtil.badRequest("Invalid filename. Filename cannot contain path separators or special characters");
            }

            // GET REQUEST BODY
            String body = event.getBody();
            if (body == null || body.isEmpty()) {
                return ResponseUtil.badRequest("Request body is required");
            }

            // DECODE BASE64 if API Gateway base64-encoded the binary data
            byte[] fileBytes;
            boolean isBase64Encoded = event.getIsBase64Encoded() != null && event.getIsBase64Encoded();

            if (isBase64Encoded) {
                try {
                    fileBytes = Base64.getDecoder().decode(body);
                } catch (IllegalArgumentException e) {
                    context.getLogger().log("Base64 decode error: " + e.getMessage());
                    return ResponseUtil.badRequest("Invalid base64 encoded content");
                }
            } else {
                // Handle as raw bytes (for text files or pre-decoded content)
                fileBytes = body.getBytes(StandardCharsets.UTF_8);
            }

            // VALIDATE FILE SIZE
            if (fileBytes.length > MAX_FILE_SIZE) {
                return ResponseUtil.badRequest(
                    String.format("File size exceeds maximum limit of %d MB", MAX_FILE_SIZE / (1024 * 1024))
                );
            }

            if (fileBytes.length == 0) {
                return ResponseUtil.badRequest("File is empty");
            }

            // EXTRACT CONTENT-TYPE from headers
            String contentType = "application/octet-stream"; // default
            Map<String, String> headers = event.getHeaders();
            if (headers != null && headers.containsKey("Content-Type")) {
                contentType = headers.get("Content-Type");
            }

            // CONSTRUCT S3 KEY (using username as prefix for organization)
            String s3Key = constructS3Key(authContext.getUsername(), filename);

            context.getLogger().log(String.format(
                "Uploading file: %s (size: %d bytes, content-type: %s) to S3 key: %s",
                filename, fileBytes.length, contentType, s3Key
            ));

            // UPLOAD TO S3
            try {
                PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(s3Key)
                    .contentType(contentType)
                    .contentLength((long) fileBytes.length)
                    .metadata(Map.of(
                        "uploaded-by", authContext.getUsername(),
                        "user-role", authContext.getRole().toString(),
                        "original-filename", filename
                    ))
                    .build();

                s3Client.putObject(putObjectRequest, RequestBody.fromBytes(fileBytes));

                context.getLogger().log("File uploaded successfully to S3: " + s3Key);

                // RETURN SUCCESS RESPONSE
                Map<String, Object> response = new HashMap<>();
                response.put("message", "File uploaded successfully");
                response.put("filename", filename);
                response.put("s3Key", s3Key);
                response.put("size", fileBytes.length);
                response.put("contentType", contentType);

                return ResponseUtil.created(response);

            } catch (S3Exception e) {
                context.getLogger().log("S3 upload error: " + e.getMessage());
                return ResponseUtil.internalServerError("Failed to upload file to storage: " + e.awsErrorDetails().errorMessage());
            }

        } catch (Exception e) {
            context.getLogger().log("Unexpected error: " + e.getMessage());
            e.printStackTrace();
            return ResponseUtil.internalServerError("Error uploading file: " + e.getMessage());
        }
    }

    /**
     * Check if user can upload files
     * Authorization rules:
     * - guest: Cannot upload
     * - user: Can upload
     * - superuser: Can upload
     * - globaladmin: Can upload
     */
    private boolean canUploadFile(AuthContext authContext) {
        if (authContext == null) {
            return false;
        }
        // All authenticated users except guests can upload
        return !authContext.isGuest();
    }

    /**
     * Validate filename to prevent path traversal and other security issues
     */
    private boolean isValidFilename(String filename) {
        if (filename == null || filename.isEmpty()) {
            return false;
        }

        // Reject path traversal attempts
        if (filename.contains("..") || filename.contains("/") || filename.contains("\\")) {
            return false;
        }

        // Reject control characters
        if (filename.matches(".*[\\x00-\\x1F\\x7F].*")) {
            return false;
        }

        // Length check
        if (filename.length() > 255) {
            return false;
        }

        return true;
    }

    /**
     * Construct S3 key with username prefix for organization
     * Pattern: uploads/{username}/{filename}
     */
    private String constructS3Key(String username, String filename) {
        return String.format("uploads/%s/%s", username, filename);
    }
}

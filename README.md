# User Management Service

A serverless user management service built with AWS CDK, API Gateway, Lambda (Java 17), DynamoDB, and Cognito for authentication.

## Architecture

- **Infrastructure**: AWS CDK (TypeScript)
- **Compute**: AWS Lambda (Java 17)
- **API**: AWS API Gateway (REST API)
- **Database**: Amazon DynamoDB
- **Authentication**: AWS Cognito User Pools
- **Build Tool**: Maven

## Features

- AWS Cognito authentication with JWT tokens
- Role-based authorization (guest, user, superuser, globaladmin)
- Create users with username, password, and role
- Get user details by ID
- List all users with pagination
- Delete users with role-based permissions
- Username uniqueness enforcement
- CORS enabled for browser access
- Comprehensive error handling
- Secure password storage in Cognito

## Authentication & Authorization

### Authentication Method
This service uses **AWS Cognito User Pools** with JWT tokens for authentication.

- **Public Endpoint**: `POST /users` (for user registration)
- **Protected Endpoints**: All other endpoints require a valid JWT token in the `Authorization` header

### Authorization Roles

| Role | POST /users | GET /users | GET /users/{id} | DELETE /users/{id} |
|------|-------------|------------|-----------------|---------------------|
| **guest** | Public registration | Read all | Read any | ❌ Cannot delete |
| **user** | Own entry only | Read all | Read any | ❌ Cannot delete |
| **superuser** | Create any | Read all | Read any | ✅ Own entry only |
| **globaladmin** | Create any | Read all | Read any | ✅ Delete any |

### Getting a JWT Token

After registering, authenticate to get a JWT token:

```bash
aws cognito-idp initiate-auth \
  --auth-flow USER_PASSWORD_AUTH \
  --client-id YOUR_USER_POOL_CLIENT_ID \
  --auth-parameters USERNAME=youruser,PASSWORD=YourPass123!
```

Extract the `IdToken` from the response and use it in the `Authorization` header:
```
Authorization: Bearer YOUR_JWT_TOKEN
```

## API Endpoints

### 1. Create User (PUBLIC)
**POST** `/users`

**Authentication**: None required (public registration)

Request body:
```json
{
  "username": "john",
  "password": "SecurePass123!",
  "role": "user"
}
```

Valid roles: `guest`, `user`, `superuser`, `globaladmin`

**Note**: Password must be at least 8 characters with uppercase, lowercase, digits, and symbols.

Response (201 Created):
```json
{
  "userId": "550e8400-e29b-41d4-a716-446655440000",
  "username": "john",
  "role": "user",
  "createdAt": 1704067200000,
  "updatedAt": 1704067200000
}
```

### 2. Get User (PROTECTED)
**GET** `/users/{userId}`

**Authentication**: Required - Bearer token

Headers:
```
Authorization: Bearer YOUR_JWT_TOKEN
```

Response (200 OK):
```json
{
  "userId": "550e8400-e29b-41d4-a716-446655440000",
  "username": "john",
  "role": "user",
  "createdAt": 1704067200000,
  "updatedAt": 1704067200000
}
```

### 3. List Users (PROTECTED)
**GET** `/users?limit=20&lastEvaluatedKey={key}`

**Authentication**: Required - Bearer token

Headers:
```
Authorization: Bearer YOUR_JWT_TOKEN
```

Query parameters:
- `limit` (optional): Number of users to return (1-100, default: 20)
- `lastEvaluatedKey` (optional): Pagination token from previous response

Response (200 OK):
```json
{
  "users": [
    {
      "userId": "550e8400-e29b-41d4-a716-446655440000",
      "username": "john",
      "role": "user",
      "createdAt": 1704067200000,
      "updatedAt": 1704067200000
    }
  ],
  "count": 1,
  "lastEvaluatedKey": "550e8400-e29b-41d4-a716-446655440000"
}
```

### 4. Delete User (PROTECTED)
**DELETE** `/users/{userId}`

**Authentication**: Required - Bearer token
**Authorization**: Requires `superuser` (own entry only) or `globaladmin` role

Headers:
```
Authorization: Bearer YOUR_JWT_TOKEN
```

Response (200 OK):
```json
{
  "message": "User deleted successfully",
  "userId": "550e8400-e29b-41d4-a716-446655440000"
}
```

## Error Responses

All errors follow this format:
```json
{
  "error": "Error message"
}
```

HTTP Status Codes:
- `400` - Bad Request (invalid input)
- `401` - Unauthorized (missing or invalid authentication token)
- `403` - Forbidden (authenticated but insufficient permissions)
- `404` - Not Found (user doesn't exist)
- `409` - Conflict (username already exists)
- `500` - Internal Server Error

## Prerequisites

- AWS Account with appropriate credentials configured
- Node.js 18+ and npm
- Java 17 (or Java 21)
- Maven 3.8+
- AWS CLI configured with credentials
- AWS CDK CLI (`npm install -g aws-cdk`)

## Project Structure

```
app-1/
├── bin/
│   └── app.ts                      # CDK app entry point
├── lib/
│   └── user-service-stack.ts       # Main CDK stack
├── lambda/
│   ├── pom.xml                     # Maven configuration
│   └── src/main/java/com/userservice/
│       ├── handler/                # Lambda handlers
│       ├── model/                  # Data models
│       └── util/                   # Utilities
├── scripts/
│   └── build-lambda.sh             # Lambda build script
├── package.json                    # CDK dependencies
├── tsconfig.json                   # TypeScript config
└── cdk.json                        # CDK configuration
```

## Installation and Deployment

### 1. Install Dependencies

Install Node.js dependencies for CDK:
```bash
npm install
```

### 2. Build Lambda Functions

Build the Java Lambda JAR:
```bash
./scripts/build-lambda.sh
```

Or manually:
```bash
cd lambda
mvn clean package
cd ..
```

This creates `lambda/target/user-service-lambda.jar`.

### 3. Bootstrap CDK (First Time Only)

If you haven't used CDK in your AWS account/region before:
```bash
cdk bootstrap
```

### 4. Build CDK

Compile the TypeScript CDK code:
```bash
npm run build
```

### 5. Deploy to AWS

Deploy the stack:
```bash
cdk deploy
```

You'll see a confirmation prompt. Type `y` to proceed.

After deployment, CDK will output the API Gateway URL:
```
Outputs:
UserServiceStack.ApiUrl = https://xxxxxxxxxx.execute-api.us-east-1.amazonaws.com/prod/
```

## Testing the API

Save the deployment outputs:
```bash
export API_URL="https://xxxxxxxxxx.execute-api.us-east-1.amazonaws.com/prod"
export USER_POOL_CLIENT_ID="your-client-id-from-cdk-output"
```

### Step 1: Register a New User (Public)
```bash
curl -X POST $API_URL/users \
  -H "Content-Type: application/json" \
  -d '{
    "username": "alice",
    "password": "SecurePass123!",
    "role": "user"
  }'
```

### Step 2: Authenticate and Get JWT Token
```bash
aws cognito-idp initiate-auth \
  --auth-flow USER_PASSWORD_AUTH \
  --client-id $USER_POOL_CLIENT_ID \
  --auth-parameters USERNAME=alice,PASSWORD=SecurePass123!
```

Extract the `IdToken` from the JSON response and save it:
```bash
export JWT_TOKEN="eyJraWQiOiJ..."  # Your IdToken from above
```

### Step 3: Use Protected Endpoints

Get a user (requires authentication):
```bash
curl $API_URL/users/{userId} \
  -H "Authorization: Bearer $JWT_TOKEN"
```

List all users (requires authentication):
```bash
curl $API_URL/users \
  -H "Authorization: Bearer $JWT_TOKEN"
```

List with pagination:
```bash
curl "$API_URL/users?limit=10" \
  -H "Authorization: Bearer $JWT_TOKEN"
```

Delete a user (requires superuser or globaladmin role):
```bash
curl -X DELETE $API_URL/users/{userId} \
  -H "Authorization: Bearer $JWT_TOKEN"
```

### Step 4: Test Authorization

Try to access a protected endpoint without a token (should return 401):
```bash
curl $API_URL/users  # Returns 401 Unauthorized
```

Try to delete a user as a regular user (should return 403):
```bash
# Create a user with 'user' role, authenticate, then try to delete
curl -X DELETE $API_URL/users/{userId} \
  -H "Authorization: Bearer $JWT_TOKEN"  # Returns 403 Forbidden
```

## Development

### Watch Mode

Run TypeScript in watch mode:
```bash
npm run watch
```

### Synthesize CloudFormation

View the generated CloudFormation template:
```bash
cdk synth
```

### View Differences

See what changes will be deployed:
```bash
cdk diff
```

### Update After Code Changes

1. Rebuild Lambda:
```bash
./scripts/build-lambda.sh
```

2. Deploy:
```bash
cdk deploy
```

## DynamoDB Schema

**Table Name**: Users

**Primary Key**:
- Partition Key: `userId` (String) - UUID

**Attributes**:
- `username` (String)
- `role` (String) - One of: guest, user, superuser, globaladmin
- `createdAt` (Number) - Unix timestamp in milliseconds
- `updatedAt` (Number) - Unix timestamp in milliseconds

**Global Secondary Index**:
- Index Name: `username-index`
- Partition Key: `username` (String)
- Purpose: Username uniqueness checks

**Billing Mode**: On-Demand (PAY_PER_REQUEST)

## Lambda Functions

All Lambda functions use:
- **Runtime**: Java 17
- **Memory**: 512 MB
- **Timeout**: 30 seconds
- **Handler Pattern**: `com.userservice.handler.{HandlerName}::handleRequest`

Functions:
1. **CreateUserFunction** - Create new users
2. **GetUserFunction** - Retrieve user by ID
3. **ListUsersFunction** - List all users with pagination
4. **DeleteUserFunction** - Delete user by ID

## Security

- CORS enabled for all origins (customize in `lib/user-service-stack.ts`)
- API throttling: 100 requests/second, burst 200
- IAM permissions follow least privilege principle
- DynamoDB encryption at rest (default AWS managed keys)
- CloudWatch logging enabled

## Cost Considerations

This service uses AWS Free Tier eligible services:
- **Lambda**: First 1M requests/month free
- **API Gateway**: First 1M requests/month free (12 months)
- **DynamoDB**: On-Demand pricing, no minimum charges

Estimated cost for low traffic: $0-5/month

## Cleanup

To delete all AWS resources:
```bash
cdk destroy
```

Note: DynamoDB table has `DESTROY` removal policy (data will be deleted). Change to `RETAIN` in production.

## Troubleshooting

### Build Errors

If Maven build fails:
```bash
cd lambda
mvn clean install -U
```

### Deployment Errors

Check CDK version:
```bash
cdk --version
```

View CloudFormation events in AWS Console for detailed error messages.

### Lambda Errors

Check CloudWatch Logs:
1. Go to AWS CloudWatch Console
2. Navigate to Log Groups
3. Find `/aws/lambda/UserService-{FunctionName}`
4. View recent log streams

## Additional Resources

- [AWS CDK Documentation](https://docs.aws.amazon.com/cdk/)
- [AWS Lambda Java Documentation](https://docs.aws.amazon.com/lambda/latest/dg/lambda-java.html)
- [DynamoDB Developer Guide](https://docs.aws.amazon.com/amazondynamodb/latest/developerguide/)
- [API Gateway REST API](https://docs.aws.amazon.com/apigateway/latest/developerguide/apigateway-rest-api.html)

## License

ISC

import * as cdk from 'aws-cdk-lib';
import { Construct } from 'constructs';
import * as dynamodb from 'aws-cdk-lib/aws-dynamodb';
import * as lambda from 'aws-cdk-lib/aws-lambda';
import * as apigateway from 'aws-cdk-lib/aws-apigateway';
import * as cognito from 'aws-cdk-lib/aws-cognito';
import * as iam from 'aws-cdk-lib/aws-iam';
import * as path from 'path';

export class UserServiceStack extends cdk.Stack {
  constructor(scope: Construct, id: string, props?: cdk.StackProps) {
    super(scope, id, props);

    // ========================================
    // DynamoDB Table
    // ========================================
    const usersTable = new dynamodb.Table(this, 'UsersTable', {
      tableName: 'Users',
      partitionKey: {
        name: 'userId',
        type: dynamodb.AttributeType.STRING,
      },
      billingMode: dynamodb.BillingMode.PAY_PER_REQUEST,
      removalPolicy: cdk.RemovalPolicy.DESTROY, // Change to RETAIN for production
      pointInTimeRecovery: true,
    });

    // Add Global Secondary Index for username uniqueness checks
    usersTable.addGlobalSecondaryIndex({
      indexName: 'username-index',
      partitionKey: {
        name: 'username',
        type: dynamodb.AttributeType.STRING,
      },
      projectionType: dynamodb.ProjectionType.ALL,
    });

    // ========================================
    // Cognito User Pool
    // ========================================
    const userPool = new cognito.UserPool(this, 'UserServiceUserPool', {
      userPoolName: 'UserServiceUserPool',
      selfSignUpEnabled: true,
      signInAliases: {
        username: true,
        email: false,
      },
      autoVerify: { email: false },
      passwordPolicy: {
        minLength: 8,
        requireLowercase: true,
        requireUppercase: true,
        requireDigits: true,
        requireSymbols: true,
      },
      accountRecovery: cognito.AccountRecovery.NONE,
      removalPolicy: cdk.RemovalPolicy.DESTROY,
      customAttributes: {
        role: new cognito.StringAttribute({ mutable: true }),
      },
    });

    // Create User Pool Client
    const userPoolClient = userPool.addClient('UserServiceClient', {
      userPoolClientName: 'UserServiceWebClient',
      authFlows: {
        userPassword: true,
        userSrp: true,
      },
      generateSecret: false,
      preventUserExistenceErrors: true,
    });

    // ========================================
    // Lambda Functions
    // ========================================
    const lambdaCodePath = path.join(__dirname, '../lambda/target/user-service-lambda.jar');

    // Common Lambda configuration
    const commonLambdaProps = {
      runtime: lambda.Runtime.JAVA_17,
      code: lambda.Code.fromAsset(lambdaCodePath),
      timeout: cdk.Duration.seconds(30),
      memorySize: 512,
      environment: {
        TABLE_NAME: usersTable.tableName,
        USER_POOL_ID: userPool.userPoolId,
        USER_POOL_CLIENT_ID: userPoolClient.userPoolClientId,
        AWS_REGION: this.region,
      },
    };

    // Create User Lambda
    const createUserFunction = new lambda.Function(this, 'CreateUserFunction', {
      ...commonLambdaProps,
      functionName: 'UserService-CreateUser',
      handler: 'com.userservice.handler.CreateUserHandler::handleRequest',
      description: 'Create a new user',
    });

    // Get User Lambda
    const getUserFunction = new lambda.Function(this, 'GetUserFunction', {
      ...commonLambdaProps,
      functionName: 'UserService-GetUser',
      handler: 'com.userservice.handler.GetUserHandler::handleRequest',
      description: 'Get user by ID',
    });

    // List Users Lambda
    const listUsersFunction = new lambda.Function(this, 'ListUsersFunction', {
      ...commonLambdaProps,
      functionName: 'UserService-ListUsers',
      handler: 'com.userservice.handler.ListUsersHandler::handleRequest',
      description: 'List all users with pagination',
    });

    // Delete User Lambda
    const deleteUserFunction = new lambda.Function(this, 'DeleteUserFunction', {
      ...commonLambdaProps,
      functionName: 'UserService-DeleteUser',
      handler: 'com.userservice.handler.DeleteUserHandler::handleRequest',
      description: 'Delete a user',
    });

    // ========================================
    // Grant DynamoDB Permissions to Lambdas
    // ========================================
    usersTable.grantReadWriteData(createUserFunction);
    usersTable.grantReadData(getUserFunction);
    usersTable.grantReadData(listUsersFunction);
    usersTable.grantReadWriteData(deleteUserFunction);

    // ========================================
    // Grant Cognito Permissions to Lambdas
    // ========================================
    // CreateUserFunction needs to create Cognito users
    createUserFunction.addToRolePolicy(new iam.PolicyStatement({
      effect: iam.Effect.ALLOW,
      actions: [
        'cognito-idp:AdminCreateUser',
        'cognito-idp:AdminSetUserPassword',
        'cognito-idp:AdminUpdateUserAttributes',
      ],
      resources: [userPool.userPoolArn],
    }));

    // DeleteUserFunction needs to delete Cognito users
    deleteUserFunction.addToRolePolicy(new iam.PolicyStatement({
      effect: iam.Effect.ALLOW,
      actions: [
        'cognito-idp:AdminDeleteUser',
      ],
      resources: [userPool.userPoolArn],
    }));

    // ========================================
    // API Gateway
    // ========================================
    const api = new apigateway.RestApi(this, 'UserServiceApi', {
      restApiName: 'User Service API',
      description: 'API for User Management Service',
      defaultCorsPreflightOptions: {
        allowOrigins: apigateway.Cors.ALL_ORIGINS,
        allowMethods: apigateway.Cors.ALL_METHODS,
        allowHeaders: [
          'Content-Type',
          'X-Amz-Date',
          'Authorization',
          'X-Api-Key',
          'X-Amz-Security-Token',
        ],
      },
      deployOptions: {
        stageName: 'prod',
        throttlingRateLimit: 100,
        throttlingBurstLimit: 200,
        loggingLevel: apigateway.MethodLoggingLevel.INFO,
        dataTraceEnabled: true,
      },
    });

    // Create Cognito Authorizer
    const cognitoAuthorizer = new apigateway.CognitoUserPoolsAuthorizer(
      this,
      'UserServiceAuthorizer',
      {
        cognitoUserPools: [userPool],
        authorizerName: 'UserServiceCognitoAuthorizer',
        identitySource: 'method.request.header.Authorization',
      }
    );

    // ========================================
    // API Resources and Methods
    // ========================================

    // /users resource
    const usersResource = api.root.addResource('users');

    // POST /users - Create user (PUBLIC - no authorizer for registration)
    usersResource.addMethod(
      'POST',
      new apigateway.LambdaIntegration(createUserFunction, {
        proxy: true,
      })
    );

    // GET /users - List all users (PROTECTED - requires authentication)
    usersResource.addMethod(
      'GET',
      new apigateway.LambdaIntegration(listUsersFunction, {
        proxy: true,
      }),
      {
        authorizer: cognitoAuthorizer,
        authorizationType: apigateway.AuthorizationType.COGNITO,
      }
    );

    // /users/{userId} resource
    const userResource = usersResource.addResource('{userId}');

    // GET /users/{userId} - Get user by ID (PROTECTED - requires authentication)
    userResource.addMethod(
      'GET',
      new apigateway.LambdaIntegration(getUserFunction, {
        proxy: true,
      }),
      {
        authorizer: cognitoAuthorizer,
        authorizationType: apigateway.AuthorizationType.COGNITO,
      }
    );

    // DELETE /users/{userId} - Delete user (PROTECTED - requires authentication)
    userResource.addMethod(
      'DELETE',
      new apigateway.LambdaIntegration(deleteUserFunction, {
        proxy: true,
      }),
      {
        authorizer: cognitoAuthorizer,
        authorizationType: apigateway.AuthorizationType.COGNITO,
      }
    );

    // ========================================
    // Stack Outputs
    // ========================================
    new cdk.CfnOutput(this, 'ApiUrl', {
      value: api.url,
      description: 'API Gateway endpoint URL',
      exportName: 'UserServiceApiUrl',
    });

    new cdk.CfnOutput(this, 'TableName', {
      value: usersTable.tableName,
      description: 'DynamoDB table name',
      exportName: 'UserServiceTableName',
    });

    new cdk.CfnOutput(this, 'CreateUserFunctionArn', {
      value: createUserFunction.functionArn,
      description: 'Create User Lambda ARN',
    });

    new cdk.CfnOutput(this, 'GetUserFunctionArn', {
      value: getUserFunction.functionArn,
      description: 'Get User Lambda ARN',
    });

    new cdk.CfnOutput(this, 'ListUsersFunctionArn', {
      value: listUsersFunction.functionArn,
      description: 'List Users Lambda ARN',
    });

    new cdk.CfnOutput(this, 'DeleteUserFunctionArn', {
      value: deleteUserFunction.functionArn,
      description: 'Delete User Lambda ARN',
    });

    new cdk.CfnOutput(this, 'UserPoolId', {
      value: userPool.userPoolId,
      description: 'Cognito User Pool ID',
      exportName: 'UserServiceUserPoolId',
    });

    new cdk.CfnOutput(this, 'UserPoolClientId', {
      value: userPoolClient.userPoolClientId,
      description: 'Cognito User Pool Client ID',
      exportName: 'UserServiceUserPoolClientId',
    });
  }
}

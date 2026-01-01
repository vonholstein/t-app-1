#!/bin/bash

# Build script for Java Lambda functions
# This script builds the Lambda JAR file using Maven

set -e  # Exit on error

echo "========================================="
echo "Building Java Lambda Functions"
echo "========================================="

# Navigate to lambda directory
cd lambda

# Clean and package
echo "Running Maven clean package..."
mvn clean package

# Check if JAR was created
if [ -f "target/user-service-lambda.jar" ]; then
    echo "========================================="
    echo "Build successful!"
    echo "JAR file: lambda/target/user-service-lambda.jar"
    echo "========================================="
else
    echo "========================================="
    echo "Build failed! JAR file not found."
    echo "========================================="
    exit 1
fi

# Return to root directory
cd ..

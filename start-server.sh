#!/bin/bash

echo "=========================================="
echo "  OpsGuide Simple - Starting Server"
echo "=========================================="
echo ""

# Check if Java is installed
if ! command -v java &> /dev/null; then
    echo "❌ Error: Java is not installed"
    echo "Please install Java 17 or higher"
    exit 1
fi

# Check Java version
JAVA_VERSION=$(java -version 2>&1 | awk -F '"' '/version/ {print $2}' | cut -d'.' -f1)
if [ "$JAVA_VERSION" -lt 17 ]; then
    echo "❌ Error: Java 17 or higher is required"
    echo "Current version: $JAVA_VERSION"
    exit 1
fi

echo "✅ Java version: $(java -version 2>&1 | head -n 1)"
echo ""

# Check if Maven is installed
if ! command -v mvn &> /dev/null; then
    echo "❌ Error: Maven is not installed"
    echo "Please install Maven 3.8 or higher"
    exit 1
fi

echo "✅ Maven version: $(mvn -version | head -n 1)"
echo ""

# Determine profile
PROFILE=${1:-default}
echo "🚀 Starting with profile: $PROFILE"
echo ""

# Build and run
echo "📦 Building project..."
mvn clean package -DskipTests

if [ $? -ne 0 ]; then
    echo "❌ Build failed"
    exit 1
fi

echo ""
echo "🎯 Starting OpsGuide Simple..."
echo "   Server: http://localhost:8080"
echo "   Health: http://localhost:8080/api/v1/health"
echo ""
echo "Press Ctrl+C to stop"
echo ""

if [ "$PROFILE" == "default" ]; then
    mvn spring-boot:run
else
    mvn spring-boot:run -Dspring-boot.run.profiles=$PROFILE
fi


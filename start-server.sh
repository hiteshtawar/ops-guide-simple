#!/bin/bash

echo "=========================================="
echo "  Production Support - Starting Server"
echo "=========================================="
echo ""
echo "Profiles:"
echo "  local - No security (default)"
echo "  dev   - JWT security enabled"
echo "  prod  - Production security"
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
PROFILE=${1:-local}
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
echo "🎯 Starting Production Support..."
echo "   Server: http://localhost:8093"
echo "   Health: http://localhost:8093/api/v1/health"
echo "   Swagger: http://localhost:8093/swagger-ui.html"
echo "   Profile: $PROFILE"
echo ""
echo "Press Ctrl+C to stop"
echo ""

mvn spring-boot:run -Dspring-boot.run.profiles=$PROFILE


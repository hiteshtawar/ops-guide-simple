# Deployment Guide
## Production Support - AWS ECS Deployment

**Version:** 1.0  
**Last Updated:** November 6, 2025

---

## Table of Contents
1. [Prerequisites](#prerequisites)
2. [Build & Publish](#build--publish)
3. [Infrastructure Setup](#infrastructure-setup)
4. [ECS Deployment](#ecs-deployment)
5. [API Gateway Configuration](#api-gateway-configuration)
6. [Monitoring Setup](#monitoring-setup)
7. [Rollback Procedures](#rollback-procedures)

---

## 1. Prerequisites

### Required Tools
- AWS CLI v2.x
- Docker 24.x+
- Maven 3.9+
- Java 17 (JDK)

### AWS Resources Required
- ECS Cluster: `ops-tools-cluster`
- VPC with private subnets
- Application Load Balancer (ALB)
- ECR Repository: `ops-guide-simple`
- API Gateway: `xxx.apigtw.com`
- CloudWatch Log Group: `/ecs/ops-guide-simple`

### IAM Roles
```bash
# ECS Task Execution Role (for pulling images, accessing secrets)
ecsTaskExecutionRole

# ECS Task Role (for application runtime permissions)
opsGuideTaskRole
```

### Secrets in AWS Secrets Manager
```bash
# Create secrets
aws secretsmanager create-secret \
  --name ops-guide/api-key \
  --secret-string "your-api-key-here"

aws secretsmanager create-secret \
  --name ops-guide/case-management-api-url \
  --secret-string "https://case-api.internal.example.com"
```

---

## 2. Build & Publish

### Step 1: Build Application

```bash
# Clone repository
git clone https://github.com/your-org/production-support-admin.git
cd production-support-admin

# Build with Maven
mvn clean package -DskipTests

# Verify JAR created
ls -lh target/ops-guide-simple-*.jar
```

### Step 2: Build Docker Image

```bash
# Build image
docker build -t ops-guide-simple:latest .

# Test locally
docker run -p 8093:8093 \
  -e SPRING_PROFILES_ACTIVE=dev \
  -e CASE_MANAGEMENT_API_BASE_URL=https://dev-api.example.com/v2 \
  ops-guide-simple:latest

# Test health endpoint
curl http://localhost:8093/api/v1/health
```

### Step 3: Push to ECR

```bash
# Authenticate to ECR
aws ecr get-login-password --region us-east-1 | \
  docker login --username AWS --password-stdin xxxxx.dkr.ecr.us-east-1.amazonaws.com

# Tag image
docker tag ops-guide-simple:latest \
  xxxxx.dkr.ecr.us-east-1.amazonaws.com/ops-guide-simple:latest

docker tag ops-guide-simple:latest \
  xxxxx.dkr.ecr.us-east-1.amazonaws.com/ops-guide-simple:v1.0.0

# Push image
docker push xxxxx.dkr.ecr.us-east-1.amazonaws.com/ops-guide-simple:latest
docker push xxxxx.dkr.ecr.us-east-1.amazonaws.com/ops-guide-simple:v1.0.0
```

---

## 3. Infrastructure Setup

### VPC & Networking

```bash
# Create security group for ECS tasks
aws ec2 create-security-group \
  --group-name ops-guide-sg \
  --description "Security group for OpsGuide ECS tasks" \
  --vpc-id vpc-xxxxx

# Allow inbound from ALB only
aws ec2 authorize-security-group-ingress \
  --group-id sg-xxxxx \
  --protocol tcp \
  --port 8093 \
  --source-group sg-alb-xxxxx

# Allow outbound to Case Management API
aws ec2 authorize-security-group-egress \
  --group-id sg-xxxxx \
  --protocol tcp \
  --port 443 \
  --cidr 10.0.0.0/8
```

### Application Load Balancer

```bash
# Create target group
aws elbv2 create-target-group \
  --name ops-guide-tg \
  --protocol HTTP \
  --port 8093 \
  --vpc-id vpc-xxxxx \
  --target-type ip \
  --health-check-enabled \
  --health-check-protocol HTTP \
  --health-check-path /api/v1/health \
  --health-check-interval-seconds 30 \
  --health-check-timeout-seconds 5 \
  --healthy-threshold-count 2 \
  --unhealthy-threshold-count 3

# Register with ALB listener
aws elbv2 create-rule \
  --listener-arn arn:aws:elasticloadbalancing:us-east-1:xxxxx:listener/app/ops-alb/xxxxx \
  --priority 100 \
  --conditions Field=path-pattern,Values='/api/v1/*' \
  --actions Type=forward,TargetGroupArn=arn:aws:elasticloadbalancing:us-east-1:xxxxx:targetgroup/ops-guide-tg
```

### CloudWatch Log Group

```bash
# Create log group
aws logs create-log-group --log-group-name /ecs/ops-guide-simple

# Set retention
aws logs put-retention-policy \
  --log-group-name /ecs/ops-guide-simple \
  --retention-in-days 90
```

---

## 4. ECS Deployment

### Step 1: Register Task Definition

Save as `task-definition.json`:
```json
{
  "family": "ops-guide-simple",
  "networkMode": "awsvpc",
  "requiresCompatibilities": ["FARGATE"],
  "cpu": "512",
  "memory": "1024",
  "containerDefinitions": [
    {
      "name": "ops-guide-simple",
      "image": "xxxxx.dkr.ecr.us-east-1.amazonaws.com/ops-guide-simple:latest",
      "portMappings": [
        {
          "containerPort": 8093,
          "protocol": "tcp"
        }
      ],
      "environment": [
        {
          "name": "SPRING_PROFILES_ACTIVE",
          "value": "prod"
        }
      ],
      "secrets": [
        {
          "name": "CASE_MANAGEMENT_API_BASE_URL",
          "valueFrom": "arn:aws:secretsmanager:us-east-1:xxxxx:secret:ops-guide/case-management-api-url"
        },
        {
          "name": "API_KEY",
          "valueFrom": "arn:aws:secretsmanager:us-east-1:xxxxx:secret:ops-guide/api-key"
        }
      ],
      "logConfiguration": {
        "logDriver": "awslogs",
        "options": {
          "awslogs-group": "/ecs/ops-guide-simple",
          "awslogs-region": "us-east-1",
          "awslogs-stream-prefix": "ecs"
        }
      },
      "healthCheck": {
        "command": ["CMD-SHELL", "curl -f http://localhost:8093/api/v1/health || exit 1"],
        "interval": 30,
        "timeout": 5,
        "retries": 3,
        "startPeriod": 60
      }
    }
  ],
  "executionRoleArn": "arn:aws:iam::xxxxx:role/ecsTaskExecutionRole",
  "taskRoleArn": "arn:aws:iam::xxxxx:role/opsGuideTaskRole"
}
```

Register the task definition:
```bash
aws ecs register-task-definition --cli-input-json file://task-definition.json
```

### Step 2: Create ECS Service

```bash
aws ecs create-service \
  --cluster ops-tools-cluster \
  --service-name ops-guide-simple-service \
  --task-definition ops-guide-simple:1 \
  --desired-count 2 \
  --launch-type FARGATE \
  --platform-version LATEST \
  --network-configuration "awsvpcConfiguration={subnets=[subnet-xxxxx,subnet-yyyyy],securityGroups=[sg-xxxxx],assignPublicIp=DISABLED}" \
  --load-balancers "targetGroupArn=arn:aws:elasticloadbalancing:us-east-1:xxxxx:targetgroup/ops-guide-tg,containerName=ops-guide-simple,containerPort=8093" \
  --health-check-grace-period-seconds 60 \
  --deployment-configuration "maximumPercent=200,minimumHealthyPercent=100"
```

### Step 3: Configure Auto Scaling

```bash
# Register scalable target
aws application-autoscaling register-scalable-target \
  --service-namespace ecs \
  --resource-id service/ops-tools-cluster/ops-guide-simple-service \
  --scalable-dimension ecs:service:DesiredCount \
  --min-capacity 2 \
  --max-capacity 10

# Create scaling policy
aws application-autoscaling put-scaling-policy \
  --service-namespace ecs \
  --resource-id service/ops-tools-cluster/ops-guide-simple-service \
  --scalable-dimension ecs:service:DesiredCount \
  --policy-name ops-guide-cpu-scaling \
  --policy-type TargetTrackingScaling \
  --target-tracking-scaling-policy-configuration file://scaling-policy.json
```

`scaling-policy.json`:
```json
{
  "TargetValue": 70.0,
  "PredefinedMetricSpecification": {
    "PredefinedMetricType": "ECSServiceAverageCPUUtilization"
  },
  "ScaleOutCooldown": 60,
  "ScaleInCooldown": 300
}
```

### Step 4: Verify Deployment

```bash
# Check service status
aws ecs describe-services \
  --cluster ops-tools-cluster \
  --services ops-guide-simple-service

# Check running tasks
aws ecs list-tasks \
  --cluster ops-tools-cluster \
  --service-name ops-guide-simple-service

# Check task health
aws ecs describe-tasks \
  --cluster ops-tools-cluster \
  --tasks task-id-xxxxx
```

---

## 5. API Gateway Configuration

### Step 1: Create API Gateway Resource

```bash
# Get API ID
API_ID=$(aws apigateway get-rest-apis --query "items[?name=='xxx.apigtw.com'].id" --output text)

# Get root resource ID
ROOT_ID=$(aws apigateway get-resources --rest-api-id $API_ID --query "items[?path=='/'].id" --output text)

# Create /production-support resource
aws apigateway create-resource \
  --rest-api-id $API_ID \
  --parent-id $ROOT_ID \
  --path-part production-support

# Create /{proxy+} resource
RESOURCE_ID=$(aws apigateway get-resources --rest-api-id $API_ID --query "items[?path=='/production-support'].id" --output text)

aws apigateway create-resource \
  --rest-api-id $API_ID \
  --parent-id $RESOURCE_ID \
  --path-part '{proxy+}'
```

### Step 2: Configure Integration

```bash
PROXY_RESOURCE_ID=$(aws apigateway get-resources --rest-api-id $API_ID --query "items[?path=='/production-support/{proxy+}'].id" --output text)

# Create ANY method
aws apigateway put-method \
  --rest-api-id $API_ID \
  --resource-id $PROXY_RESOURCE_ID \
  --http-method ANY \
  --authorization-type AWS_IAM \
  --request-parameters "method.request.path.proxy=true"

# Set up integration
aws apigateway put-integration \
  --rest-api-id $API_ID \
  --resource-id $PROXY_RESOURCE_ID \
  --http-method ANY \
  --type HTTP_PROXY \
  --integration-http-method ANY \
  --uri "http://internal-alb-xxxxx.us-east-1.elb.amazonaws.com/api/v1/{proxy}" \
  --request-parameters "integration.request.path.proxy=method.request.path.proxy"
```

### Step 3: Deploy API

```bash
# Deploy to stage
aws apigateway create-deployment \
  --rest-api-id $API_ID \
  --stage-name prod \
  --description "Deploy Production Support integration"

# Test endpoint
curl -X POST https://xxx.apigtw.com/production-support/process \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <token>" \
  -d '{"query": "cancel case 2025123P6732"}'
```

---

## 6. Monitoring Setup

### CloudWatch Alarms

```bash
# High error rate alarm
aws cloudwatch put-metric-alarm \
  --alarm-name ops-guide-high-error-rate \
  --alarm-description "Alert when error rate exceeds 5%" \
  --metric-name 5XXError \
  --namespace AWS/ECS \
  --statistic Sum \
  --period 300 \
  --threshold 5 \
  --comparison-operator GreaterThanThreshold \
  --evaluation-periods 1 \
  --alarm-actions arn:aws:sns:us-east-1:xxxxx:ops-alerts

# High latency alarm
aws cloudwatch put-metric-alarm \
  --alarm-name ops-guide-high-latency \
  --alarm-description "Alert when p99 latency exceeds 1s" \
  --metric-name TargetResponseTime \
  --namespace AWS/ApplicationELB \
  --statistic Average \
  --period 300 \
  --threshold 1000 \
  --comparison-operator GreaterThanThreshold \
  --evaluation-periods 2 \
  --alarm-actions arn:aws:sns:us-east-1:xxxxx:ops-alerts

# Low healthy task count
aws cloudwatch put-metric-alarm \
  --alarm-name ops-guide-low-task-count \
  --alarm-description "Alert when healthy task count < 1" \
  --metric-name HealthyHostCount \
  --namespace AWS/ApplicationELB \
  --statistic Average \
  --period 60 \
  --threshold 1 \
  --comparison-operator LessThanThreshold \
  --evaluation-periods 2 \
  --alarm-actions arn:aws:sns:us-east-1:xxxxx:ops-alerts
```

### CloudWatch Dashboard

```bash
# Create dashboard
aws cloudwatch put-dashboard \
  --dashboard-name ProductionSupport-Metrics \
  --dashboard-body file://dashboard.json
```

`dashboard.json`:
```json
{
  "widgets": [
    {
      "type": "metric",
      "properties": {
        "metrics": [
          ["AWS/ECS", "CPUUtilization", {"stat": "Average"}],
          [".", "MemoryUtilization", {"stat": "Average"}]
        ],
        "period": 300,
        "stat": "Average",
        "region": "us-east-1",
        "title": "ECS Resource Utilization"
      }
    },
    {
      "type": "metric",
      "properties": {
        "metrics": [
          ["AWS/ApplicationELB", "RequestCount", {"stat": "Sum"}],
          [".", "TargetResponseTime", {"stat": "Average"}]
        ],
        "period": 300,
        "region": "us-east-1",
        "title": "API Metrics"
      }
    }
  ]
}
```

---

## 7. Rollback Procedures

### Rollback to Previous Task Definition

```bash
# List task definition revisions
aws ecs list-task-definitions --family-prefix ops-guide-simple

# Update service to use previous version
aws ecs update-service \
  --cluster ops-tools-cluster \
  --service ops-guide-simple-service \
  --task-definition ops-guide-simple:PREVIOUS_VERSION

# Monitor rollback
aws ecs describe-services \
  --cluster ops-tools-cluster \
  --services ops-guide-simple-service \
  --query 'services[0].deployments'
```

### Emergency Rollback (Scale to Zero)

```bash
# Scale service to 0
aws ecs update-service \
  --cluster ops-tools-cluster \
  --service ops-guide-simple-service \
  --desired-count 0

# Remove from API Gateway (temporary)
aws apigateway update-method \
  --rest-api-id $API_ID \
  --resource-id $PROXY_RESOURCE_ID \
  --http-method ANY \
  --patch-operations op=replace,path=/authorizationType,value=NONE

# Deploy API change
aws apigateway create-deployment \
  --rest-api-id $API_ID \
  --stage-name prod
```

---

## Testing Checklist

### Post-Deployment Verification

- [ ] Health check endpoint responds: `GET /api/v1/health`
- [ ] Actuator endpoints accessible: `GET /actuator/health`
- [ ] Classification works: `POST /api/v1/classify`
- [ ] Full process works: `POST /api/v1/process`
- [ ] Step execution works: `POST /api/v1/execute-step`
- [ ] Logs appearing in CloudWatch
- [ ] Metrics showing in CloudWatch dashboard
- [ ] ALB target health is healthy
- [ ] Auto-scaling triggers on load test
- [ ] API Gateway returns 200 (not 502/503/504)

### Load Testing

```bash
# Simple load test with Apache Bench
ab -n 1000 -c 10 -H "Authorization: Bearer <token>" \
  -p request.json -T application/json \
  https://xxx.apigtw.com/ops-guide/process

# Expected results:
# - 0% error rate
# - p99 latency < 500ms
# - Throughput > 100 req/s
```

---

## Troubleshooting

### Issue: Tasks Keep Restarting

**Check:**
```bash
# View task logs
aws logs tail /ecs/ops-guide-simple --follow

# Common causes:
# - Health check failing (check /api/v1/health)
# - Insufficient memory (OOMKilled)
# - Missing secrets/environment variables
```

**Solution:**
```bash
# Increase memory
aws ecs register-task-definition --cli-input-json file://task-definition.json
# (Edit task-definition.json, increase memory to 2048)

# Fix secrets
aws secretsmanager get-secret-value --secret-id ops-guide/api-key
```

### Issue: 502 Bad Gateway from API Gateway

**Check:**
```bash
# Verify ALB target health
aws elbv2 describe-target-health \
  --target-group-arn arn:aws:elasticloadbalancing:us-east-1:xxxxx:targetgroup/ops-guide-tg

# Check security group rules
aws ec2 describe-security-groups --group-ids sg-xxxxx
```

### Issue: Slow Response Times

**Check:**
```bash
# View ECS service events
aws ecs describe-services \
  --cluster ops-tools-cluster \
  --services ops-guide-simple-service \
  --query 'services[0].events'

# Check downstream API latency
aws logs filter-pattern /ecs/ops-guide-simple --filter-pattern "duration"
```

---

**Last Updated:** November 6, 2025  
**Next Review:** December 6, 2025


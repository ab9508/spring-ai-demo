# ============================================
# Spring AI Demo - Dockerfile
# 构建：docker build -t spring-ai-demo .
# 多阶段构建：构建阶段 + 运行阶段
# ============================================

# === 构建阶段 ===
# 用 Maven + JDK17 镜像来编译
FROM maven:3.9-eclipse-temurin-17 AS builder

WORKDIR /build

# 先复制 pom.xml，利用 Docker 缓存层
COPY pom.xml .
RUN mvn dependency:resolve -q -B

# 复制源码并构建
COPY src ./src
RUN mvn clean package -DskipTests -q -B

# === 运行阶段 ===
# 用轻量级 JDK17 镜像运行
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

# 从构建阶段复制 JAR
COPY --from=builder /build/target/spring-ai-demo-1.0.0.jar app.jar

# 暴露的端口
# 8080 = 主应用  |  8081 = MCP Server  |  8082 = MCP Client
EXPOSE 8080 8081 8082

# 入口：通过 SPRING_PROFILES_ACTIVE 环境变量切换
# docker run -e SPRING_PROFILES_ACTIVE=mcp-server spring-ai-demo
ENTRYPOINT ["java", "-jar", "app.jar"]

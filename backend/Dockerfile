# backend/Dockerfile
FROM maven:3.9.4-eclipse-temurin-17 AS builder

WORKDIR /app

# 复制 pom.xml 和自定义 settings.xml
COPY pom.xml .
COPY settings.xml .

# 使用自定义 settings.xml 离线下载依赖
RUN mvn -B dependency:go-offline \
    -s settings.xml \
    -Dmaven.repo.local=./.m2

# 复制源码
COPY src ./src

# 构建 JAR（跳过测试）
RUN mvn -B package \
    -s settings.xml \
    -Dmaven.repo.local=./.m2 \
    -DskipTests

# 运行阶段
FROM eclipse-temurin:17-jre

WORKDIR /app

# 安装基础工具（curl 用于 healthcheck）
RUN apt-get update && apt-get install -y curl wget && rm -rf /var/lib/apt/lists/*

# 创建必要目录
RUN mkdir -p /app/uploads /app/results /app/logs /app/scripts

# 复制 JAR

COPY --from=builder /app/target/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
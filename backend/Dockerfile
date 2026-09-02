# syntax=docker/dockerfile:1.7

# Build stage
FROM maven:3.9-eclipse-temurin-17-alpine AS build
WORKDIR /app
# 1. 先拷贝 pom.xml 以便缓存依赖
COPY pom.xml .
# 2. 预下载依赖；缓存挂载会在 BuildKit 中断后保留已下载文件
RUN --mount=type=cache,target=/root/.m2/repository,sharing=locked mvn dependency:go-offline -B
# 3. 再拷贝源码并编译
COPY src ./src
RUN --mount=type=cache,target=/root/.m2/repository,sharing=locked mvn clean package -DskipTests

# Run stage
FROM eclipse-temurin:17-jdk
WORKDIR /app
COPY --from=build /app/target/ai-interview-backend-1.0.0-SNAPSHOT.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]

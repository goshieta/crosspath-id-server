# Multi-stage build
# Stage 1: Build with Maven
FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /app

# 依存関係のキャッシュ: pom.xml を先にコピーして offline ダウンロード
COPY pom.xml .
RUN mvn -B dependency:go-offline -DskipTests

# ソースコードをコピーしてビルド
COPY src src
RUN mvn -B -DskipTests package -Dspring.boot.maven-plugin.skip=true

# Stage 2: Runtime with minimal JRE
FROM eclipse-temurin:21-jre-alpine

# 非 root ユーザの作成
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

WORKDIR /app

# build ステージから jar をコピー
COPY --from=build /app/target/*.jar app.jar

RUN chown -R appuser:appgroup /app

USER appuser

EXPOSE 8080

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
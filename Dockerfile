# ==========================================
# Dockerfile cho DineFlow Backend Spring Boot
# Tối ưu hóa bộ nhớ cho VPS & Timezone GMT+7
# ==========================================

# Stage 1: Build JAR bằng Maven
FROM maven:3.9.6-eclipse-temurin-17-alpine AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn clean package -DskipTests

# Stage 2: Runtime với Eclipse Temurin 17 JRE Alpine siêu nhẹ (~150MB)
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# Thiết lập múi giờ Việt Nam GMT+7
ENV TZ=Asia/Ho_Chi_Minh
RUN apk add --no-cache tzdata && \
    cp /usr/share/zoneinfo/$TZ /etc/localtime && \
    echo $TZ > /etc/timezone

# Copy file JAR thành phẩm từ Stage 1
COPY --from=build /app/target/*.jar app.jar

EXPOSE 8000

# Chạy JVM với cấu hình tối ưu bộ nhớ 70% RAM, G1GC và múi giờ GMT+7
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=70.0", "-XX:+UseG1GC", "-Duser.timezone=Asia/Ho_Chi_Minh", "-jar", "app.jar"]

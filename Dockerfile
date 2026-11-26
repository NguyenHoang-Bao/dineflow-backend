# ==========================================
# Dockerfile cho DineFlow Backend Spring Boot
# Tối ưu hóa bộ nhớ cho VPS / AWS EC2 (Java 17 JRE Alpine)
# Chuẩn hóa như dự án Hoang Bao Cosmetics
# ==========================================

FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

# Thiết lập múi giờ Việt Nam GMT+7
ENV TZ=Asia/Ho_Chi_Minh
RUN apk add --no-cache tzdata && \
    cp /usr/share/zoneinfo/$TZ /etc/localtime && \
    echo $TZ > /etc/timezone

# Sao chép file JAR thành phẩm đã build từ Maven
ARG JAR_FILE=target/*.jar
COPY ${JAR_FILE} app.jar

# DineFlow chạy tại cổng 8000 (không xung đột với hoangbaocosmetics chạy cổng 8080)
EXPOSE 8000

# Chạy JVM với cấu hình tối ưu bộ nhớ 70% RAM, G1GC và múi giờ GMT+7
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=70.0", "-XX:+UseG1GC", "-Duser.timezone=Asia/Ho_Chi_Minh", "-jar", "app.jar"]

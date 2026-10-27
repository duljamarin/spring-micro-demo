FROM eclipse-temurin:25-jre-alpine
WORKDIR /app
COPY target/review-service-1.0.0.jar app.jar
EXPOSE 8088
ENTRYPOINT ["java", "-jar", "app.jar"]

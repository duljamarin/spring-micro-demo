FROM eclipse-temurin:25-jre-alpine
WORKDIR /app
COPY target/cart-service-1.0.0.jar app.jar
EXPOSE 8085
ENTRYPOINT ["java", "-jar", "app.jar"]

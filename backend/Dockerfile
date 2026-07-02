# Uses the pre-built JAR from local Maven build (mvn package -DskipTests)
# This avoids re-downloading all Maven dependencies inside Docker
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN apk add --no-cache curl
COPY simulation-engine/target/simulation-engine-*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]

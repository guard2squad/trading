# Build stage
FROM eclipse-temurin:17-jre AS runtime

# Set the working directory in the Docker image for the runtime stage
WORKDIR /app

# Copy the JAR file from the context directly into the runtime image.
COPY web/build/libs/web-0.0.1-SNAPSHOT.jar trading.jar

# Command to run the application
ENTRYPOINT ["java", "-jar", "trading.jar"]

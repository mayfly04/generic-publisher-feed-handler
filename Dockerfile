# Use official Java 21 base image
FROM eclipse-temurin:21-jdk

# Set working directory
WORKDIR /app

# Copy everything into the container
COPY . .

# Set environment variables required by KX RT
ENV RT_REP_DIR=/app/rt
ENV RT_LOG_PATH=/app/logs

# Create logs directory
RUN mkdir -p /app/logs

# Install Maven (required to build the project)
RUN apt-get update && \
    apt-get install -y maven && \
    apt-get clean

# Build the application
RUN mvn clean package -DskipTests
#RUN mvn install

# Set entrypoint and default config
ENTRYPOINT ["java", "-jar", "target/KX-Fix-Initiator.jar"]
CMD ["src/main/resources/fix-acceptor.cfg"]
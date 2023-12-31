# Use a base image with a Java runtime environment
FROM adoptopenjdk:11-jre-hotspot

# Set the working directory inside the container
WORKDIR /app

# Copy the JAR file into the container
COPY app/build/libs/app-1.0.jar .

# Set the command to run your application
CMD ["java", "-jar", "app-1.0.jar", "com.App"]

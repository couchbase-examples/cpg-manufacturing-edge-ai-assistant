FROM ubuntu:22.04

# Install Java JDK, Maven, system dependencies, and ICU libraries for Couchbase Lite
RUN apt-get update && apt-get install -y \
    openjdk-21-jdk \
    maven \
    curl \
    wget \
    git \
    unzip \
    && rm -rf /var/lib/apt/lists/*

# Set working directory
WORKDIR /app

# Download and setup Couchbase Lite Linux support libraries
RUN wget https://packages.couchbase.com/releases/couchbase-lite-java/3.2.4/couchbase-lite-java-linux-supportlibs-3.2.4.zip && \
    unzip couchbase-lite-java-linux-supportlibs-3.2.4.zip && \
    rm couchbase-lite-java-linux-supportlibs-3.2.4.zip

# Set up the Native Libraries for Linux
ENV LD_LIBRARY_PATH=/app/couchbase-lite-java-3.2.4:$LD_LIBRARY_PATH

# Copy Maven configuration
COPY pom.xml .

# Download dependencies (cache this layer)
RUN mvn dependency:go-offline -B

# Copy source code
COPY src ./src

# Create data directory
RUN mkdir -p /app/data

# Expose port for future web interface
EXPOSE 8080

# Default command - build and run the application
CMD ["bash", "-c", "mvn clean compile -q && java -cp target/classes:$(mvn dependency:build-classpath -q -Dmdep.outputFile=/dev/stdout) com.couchbase.example.iot_device.CPGIoTTelemetryGenerator"] 
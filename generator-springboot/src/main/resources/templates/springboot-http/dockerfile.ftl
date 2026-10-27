FROM eclipse-temurin:17-jdk-alpine
COPY target/${handlerInfo.artifactId}-${handlerInfo.version}-${handlerInfo.classifier}.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]

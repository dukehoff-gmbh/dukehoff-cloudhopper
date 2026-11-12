FROM eclipse-temurin:21-jre-alpine
COPY target/${handlerInfo.artifactId}-${handlerInfo.version}-${handlerInfo.classifier}.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]

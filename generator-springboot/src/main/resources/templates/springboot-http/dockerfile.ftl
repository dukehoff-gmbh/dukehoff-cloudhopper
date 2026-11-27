<#if (runtime!"jvm") == "native">
# A native image of the Spring Boot application (-Aruntime=native): the jar was built with
# Spring AOT (process-aot); GraalVM compiles it in the first stage. This takes a few minutes
# and a few GB of memory; the image is built for the platform of the build (--platform).
FROM ghcr.io/graalvm/native-image-community:25.0.2 AS build
WORKDIR /build
# options native-image reads from the environment, e.g. --build-arg NATIVE_IMAGE_OPTIONS=-J-Xmx3g
# to keep the compiler within the memory Docker has
ARG NATIVE_IMAGE_OPTIONS=""
COPY target/${handlerInfo.artifactId}-${handlerInfo.version}-${handlerInfo.classifier}.jar app.jar
# the executable jar unpacked: Spring Boot keeps the AOT metadata and the main class at its root
# (META-INF/native-image); the jar launcher and the jar mode tools are only for java -jar
RUN mkdir app && cd app && jar xf ../app.jar \
 && rm -rf org/springframework/boot/loader META-INF/services/java.nio.file.spi.FileSystemProvider \
 && cp=".:BOOT-INF/classes:$(ls BOOT-INF/lib/*.jar | grep -v '/spring-boot-jarmode-' | tr '\n' ':')" \
 && native-image -cp "$cp" -o /build/application

FROM debian:bookworm-slim
COPY --from=build /build/application /application
EXPOSE 8080
ENTRYPOINT ["/application"]
<#else>
FROM eclipse-temurin:21-jre-alpine
COPY target/${handlerInfo.artifactId}-${handlerInfo.version}-${handlerInfo.classifier}.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
</#if>

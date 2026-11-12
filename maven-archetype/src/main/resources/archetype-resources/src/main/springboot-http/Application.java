package ${package};

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Starts the functions of this project as a Spring Boot service (profile springboot-http):
 * {@code mvn -Pspringboot-http package}, then
 * {@code java -jar target/<artifactId>-<version>-springboot-http.jar} (or the generated Dockerfile).
 * Cloudhopper generates the controllers into this package; this class is yours to configure.
 */
@SpringBootApplication
public class Application {

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}

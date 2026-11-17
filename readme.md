# Dukehoff Cloudhopper

[![Maven Central](https://img.shields.io/maven-central/v/eu.cloudhopper.mc/deployment-config-api)](https://central.sonatype.com/namespace/eu.cloudhopper.mc)

Serverless Java that doesn't know which cloud it runs on. Annotate a plain method, pick the
target in the build, and Cloudhopper generates the handler classes and the deployment
configuration for it: Terraform for AWS Lambda, Azure Functions and Google Cloud Functions,
or a Spring Boot application for your own servers.

```java
public class HelloFunction {

    @Function(name = "hello")
    @HttpTrigger(method = "GET", path = "/hello/{name}")
    public String hello(@PathParam String name) {
        return "hello " + name;
    }
}
```

```bash
mvn clean install -Paws-terraform   # or -Pazure-terraform, -Pgcp-terraform, -Pspringboot-http
```

## Documentation

- [Quickstart](https://cloudhopper.eu/docs/guides/quickstart)
- [Documentation](https://cloudhopper.eu/docs/intro)
- [Supported features per target](https://cloudhopper.eu/docs/platforms/generator-overview)

The documentation lives in [`docs/`](docs) (Docusaurus) and nowhere else. Module READMEs
only point to the matching page.

## License

Dukehoff Cloudhopper is developed by [Dukehoff GmbH](https://github.com/dukehoff-gmbh) and licensed under
the [GNU General Public License v3](LICENSE) with two additional permissions (see [LICENSE-EXCEPTIONS](LICENSE-EXCEPTIONS)):

- **Classpath Exception** – you may use the Cloudhopper libraries in applications under any license.
- **Generator Output Exception** – the code and infrastructure files Cloudhopper generates for your
  project are yours to use under terms of your choice.

For commercial licensing options, please contact Dukehoff GmbH.

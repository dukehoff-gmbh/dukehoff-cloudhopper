# Dukehoff Cloudhopper

**You code, we take out the YAML**

Cloudhopper turns annotated Java methods into serverless functions. You write a plain
method and pick the target in the build; Cloudhopper generates the handler classes and
the deployment configuration: Terraform, OpenAPI descriptions, a Helm chart or a
Spring Boot application. You write no deployment files, and adding a function adds no
copy-paste.

```java
@Function(name = "hello")
@HttpTrigger(method = "GET", path = "/hello/{name}")
public String hello(@PathParam String name) {
    return "hello " + name;
}
```

## One code base, six targets

The same code builds for each target with a Maven profile:

- **AWS Lambda**, **Azure Functions** and **Google Cloud Functions**, with Terraform
- **Spring Boot**, as a service on your own servers or a local machine
- **Knative**, as a Helm chart for any Kubernetes cluster
- **Scaleway Serverless Containers**, with Terraform

Beyond HTTP, functions use schedules, queue and storage events, send messages with
`@Queue`, read configuration and secrets with MicroProfile Config, and run with their
own permissions per identity. What each target generates is described on its page under
[Cloud Providers](platforms/generator-overview.md).

## You see what a target supports

- **Declared support:** every target declares the annotations and attributes it
  supports. The [support matrix](platforms/generator-overview.md#support-matrix) is
  generated from these declarations.
- **Build warnings:** when you build for a target, the build warns about everything
  the target would ignore, so nothing is dropped silently.
- **Compatibility tests:** a test suite (TCK) deploys the same test functions to every
  target in the cloud and checks every declared feature.

## The output is yours

The generated Terraform and Java are plain, readable files in `target/`. You can apply
them with your own pipeline, check them into a repository, or keep them if you stop
using Cloudhopper; the license leaves the generated code to you. To change what is
generated, override a template or add a target of your own through the service
interfaces; see [Customizing targets](guides/customizing-targets.md).

## Get started

- [Quickstart](guides/quickstart.md): a function running locally in two minutes, then
  in the cloud
- [Writing functions](guides/writing-functions.md): triggers, events, messages,
  configuration, identities
- [Cloud Providers](platforms/generator-overview.md): what each target generates and
  supports

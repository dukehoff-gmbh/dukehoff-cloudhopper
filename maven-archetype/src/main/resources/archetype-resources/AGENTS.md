#set($h = '#')
$h Instructions for coding agents

This is a Cloudhopper project: serverless Java functions that are deployed to several
targets from the same code. Cloudhopper generates the handler classes and the deployment
configuration (Terraform, OpenAPI, a Helm chart or a Spring Boot application) from
annotations at compile time. Documentation: https://cloudhopper.eu/docs/intro

For details, load the skill `cloudhopper` (in `.agents/skills/` and `.claude/skills/`).

$h$h Rules

- **Functions are annotated methods.** Write a plain method with `@Function` and a trigger
  (`@HttpTrigger`, `@ScheduledTrigger`, `@EventTrigger`). Do not write handler classes,
  Lambda/Azure/GCP entry points, controllers or Terraform for functions by hand: the build
  generates them.
- **Never edit files under `target/`.** `target/deployment/<target>/` and the generated
  sources are rewritten by every build.
- **Your own infrastructure goes into `src/main/resources/deployment/<target>/`** (provider,
  backend, variables, extra resources and permissions). The build copies it next to the
  generated Terraform. To change what is generated for every function, override a template
  in `src/main/resources/cloudhopper-templates/<target>/`.
- **Build warnings matter.** A warning that a target does not support an annotation or
  attribute means the target ignores it. Change the code or choose another target; do not
  suppress it.
- **Use the Cloudhopper APIs before cloud SDKs:** `@Queue` with `Publisher<T>` to send
  messages, `@ConfigProperty` (MicroProfile Config) for configuration and secrets,
  `@Function(identity)` for permissions. A cloud SDK in a function ties it to one target.
- **Values per deployment** go into the Terraform variables `cloudhopper_config` (values)
  and `cloudhopper_secrets` (references to secrets), never into code.

$h$h Build

The targets of this project are Maven profiles:

#foreach($generator in $generatorIds.split(","))
- `mvn clean package -P${generator.trim()}`
#end

Run the build after every change: the annotation processor checks the annotations, and
the build fails on invalid ones. The deployment is written to `target/deployment/<target>/`.
#if($generatorIds.contains("springboot-http"))

`-Pspringboot-http` runs everything locally without a cloud account:
`java -jar target/${artifactId}-${version}-springboot-http.jar`, then
`curl localhost:8080/...`.
#end

Functions live in `src/main/java/${package.replace('.', '/')}`.

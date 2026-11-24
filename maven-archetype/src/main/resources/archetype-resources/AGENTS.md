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
- **Zero cloud SDKs: never import provider-specific SDK types in function classes**
  (`com.amazonaws.*`, `software.amazon.awssdk.*`, `com.google.cloud.*`, `com.azure.*`,
  `com.microsoft.azure.*`, `com.scaleway.*`, …), and do not depend on provider-specific
  resources, environment variables or event formats. They tie the function to one target and
  break the other profiles. Use the Cloudhopper APIs instead: `@Queue` with `Publisher<T>` to
  send messages, `@Bucket` with `BlobStore` to read and write objects, `@Table` with
  `KeyValueStore` to read and write items, `@ConfigProperty` (MicroProfile Config) for
  configuration and secrets, `@EventTrigger` and `StorageEvent` for events,
  `@Function(identity)` for permissions.
- **When there is no Cloudhopper API for something**, keep the function class portable: put the
  provider-specific code behind an interface of your own in a separate class, choose the
  implementation through configuration, and say which targets it supports.
- **Vendor-specific settings** go only through `@Extension` on `@Function`; other targets
  ignore them.
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

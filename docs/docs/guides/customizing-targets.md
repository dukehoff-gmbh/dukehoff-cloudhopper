# Customizing targets

Every target is a set of templates that the annotation processor renders at
compile time. You can change what a target generates without forking Cloudhopper,
in three steps of increasing effort:

| You want to | Do this |
|---|---|
| Add resources or permissions next to the generated ones | your own `.tf` files (or Helm values) next to the generated ones |
| Change what a target generates for every function | [override a template](#override-a-template) in your project |
| Change a target substantially or support another platform | [write a generator](#write-your-own-target) of your own |

## Add your own configuration

The generated Terraform is plain Terraform in `target/deployment/<target>/`, next
to your own `main.tf`. Resources, policies and outputs in your own `.tf` files can
refer to the generated resources, for example to attach a policy to the role of an
[identity](writing-functions.md#identities-and-permissions). This needs no
knowledge of the templates and survives upgrades best.

## Override a template

A template of the same name in your project replaces the target's template:

```
src/main/resources/cloudhopper-templates/<target>/<template>.ftl
```

For example, `src/main/resources/cloudhopper-templates/aws-terraform/function.ftl`
replaces the template that generates each Lambda function. The processor looks in
your project first, then in the generator.

1. Find the template: the tables in
   [Targets and what they support](../platforms/generator-overview.md#what-each-target-generates)
   name the templates of each target and what they generate.
2. Copy it from the generator jar (`templates/<target>/<template>.ftl`, e.g. in
   `generator-aws-terraform-<version>.jar`) to the path above, then change it.
3. Build with the target's profile and check the result in
   `target/deployment/<target>/`.

Templates are [FreeMarker](https://freemarker.apache.org/) templates. They get the
function's data as `handlerInfo` (name, memory, timeout, identity, configuration
properties, queues, extensions …) and, depending on the template, the trigger's
attributes.

An overridden template no longer follows changes of the generator: check it
against the generator's template when you upgrade Cloudhopper. Keep overrides
small, and prefer your own configuration files where they are enough.

## Write your own target

A target is a generator module: templates plus two annotations on a class.

```java
@TemplateRegistration(
    generatorId = "my-cloud",
    templates = {
        @Template(phase = GenerationPhase.FUNCTION, templateName = "function.ftl",
                  outputFileExtension = "tf", outputSubDirectory = "", javaFile = false),
        // …
    })
@GeneratorFeatures(
    generatorId = "my-cloud",
    supportedFeatures = {
        @GeneratorFeature(supportedAnnotation = Function.class,
                          supportedAttributes = {FunctionAttribute.NAME, FunctionAttribute.MEMORY}),
        // …
    })
public class MyCloudTemplateRegistration {
}
```

- `@TemplateRegistration` lists the templates (under `templates/my-cloud/` in the
  module) and the [generation phase](../modules/deployment-config-generator-generic.md#generation-phases)
  each one belongs to. With `basedOn = "springboot-http"`, a target builds on
  another one: it runs that target's templates as well and replaces those of the
  same name. The `knative` and `scaleway-terraform` targets are built this way on
  top of the Spring Boot target.
- `@GeneratorFeatures` declares which annotations and attributes the target
  supports. The build warns about everything a function uses that the target does
  not support, and the [support matrix](../platforms/generator-overview.md#support-matrix)
  is generated from these declarations.
- A project uses the target like the built-in ones: the generator on the
  processor path and `-AgeneratorId=my-cloud` (see
  [Maven configuration](../platforms/generator-overview.md#maven-configuration)).
- If the platform calls functions differently from the existing targets, the
  target also needs a runtime module with handlers that adapt the platform's events
  to `CloudRequestHandler` (see `provider-aws` for an example).

The compatibility tests (TCK) check a target against what it declares: a binding
deploys the test functions to the platform and lets the tests call them; every
test whose features the target declares has to pass. The built-in targets are
certified this way on every change.

See [deployment-config-generator-generic](../modules/deployment-config-generator-generic.md)
and [deployment-config-generator-api](../modules/deployment-config-generator-api.md)
for the details of the generator SPI.

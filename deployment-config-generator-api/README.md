---
title: Deployment Config Generator API
---

# deployment-config-generator-api

This module provides the public extension API for implementing custom deployment configuration generators in Cloudhopper.

It defines the **SPI**, annotations, and support types used by integrators to declare and implement generators that transform annotated Java functions into provider-specific deployment artifacts (e.g., Terraform, CloudFormation, etc.).

---

## 💡 Purpose

This module is intended for:
- Platform integrators building custom code generators
- Template-based generators declaring supported features
- The Cloudhopper annotation processor, which uses this metadata to validate annotations and orchestrate generation

---

## 🔌 Key Components

### SPI

- `DeploymentConfigGenerator`:  
  Main interface to be implemented by generator modules.

### Annotations

- `@GeneratorFeatures`:  
  Declares the generator ID and supported features.
- `@GeneratorFeature`:  
  Describes which annotations and attributes a generator supports.

### Support Types

- `HandlerInfo`:  
  Encapsulates function metadata passed to generators.
- `GenerationPhase`:  
  Enum describing the stages of generation (e.g. FUNCTION, API, etc.)
- `ConfigGenerationException`:  
  Used to signal errors during generation.
---

## 🧩 Example

~~~java
@GeneratorFeatures(
    generatorId = "aws-terraform",
    supportedFeatures = {
        @GeneratorFeature(
            supportedAnnotation = Function.class,
            supportedAttributes = {
                FunctionAttribute.NAME,
                FunctionAttribute.MEMORY,
                FunctionAttribute.TIMEOUT
            }
        )
    }
)
@TemplateRegistration(
    generatorId = "aws-terraform",
    templates = {
        @Template(
            phase = GenerationPhase.FUNCTION,
            templateName = "handler.ftl",
            outputFileExtension = "java",
            outputSubDirectory = "generated",
            javaFile = true
        )
    }
)
public class AwsTemplateRegistration {
    // This class is solely for generator feature and template registration
}
~~~

---

## 📦 Package Overview

| Package                                      | Description                                  |
|---------------------------------------------|----------------------------------------------|
| `eu.cloudhopper.mc.generator.api`          | Support types and enums                      |
| `eu.cloudhopper.mc.generator.api.spi`      | SPI interface to be implemented by generators|
| `eu.cloudhopper.mc.generator.api.annotations` | Metadata annotations for declaring generator features |

---

## 🚀 Related Modules

- `deployment-config-processor`: consumes this SPI to perform annotation processing
- `deployment-config-generator-generic`: default implementation using templates

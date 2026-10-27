---
title: Generic Deployment Config Generator
---

# deployment-config-generator-generic

This module provides a flexible, template-based generator for Cloudhopper that allows integrators to define deployment configurations using Freemarker templates and simple annotations.

It implements the `DeploymentConfigGenerator` SPI and is used as the default generator for most Cloudhopper setups.

---

## 💡 Purpose

This module enables:
- Code generation from annotations without writing Java generator logic
- Declarative registration of templates for each generation phase
- Easy support for multiple providers with different output formats (e.g., Terraform, SAM)

---

## 🔧 Usage

To use this generator, create a class annotated with `@TemplateRegistration` and list all templates used by your provider.

### Example

  
~~~java
@TemplateRegistration(
    generatorId = "aws-terraform",
    templates = {
        @Template(
            phase = GenerationPhase.FUNCTION,
            templateName = "function",
            outputFileExtension = "tf",
            outputSubDirectory = "deployment/aws",
            description = "Main Lambda function"
        ),
        @Template(
            phase = GenerationPhase.SHARED,
            templateName = "shared",
            outputFileExtension = "tf",
            outputSubDirectory = "deployment/aws",
            description = "Shared resources like IAM roles"
        )
    }
)
public class AwsTerraformJava21TemplateRegistration { }
~~~

This tells Cloudhopper to:
- Use the generic generator
- Load templates from `/templates/aws-terraform/`
- Render those templates into the specified subdirectories

---

## 📦 Template Directory Structure

Your templates must be available in:

  
~~~
src/main/resources/templates/<generatorId>/
~~~

Example:

  
~~~
src/main/resources/templates/aws-terraform/
├── function.ftl
├── shared.ftl
├── doc/
│   ├── README.md
│   └── doc.index
~~~

You can optionally include documentation resources (like generated instructions or Markdown files) in a `doc/` subfolder and list them in `doc.index`.

---

## 🧩 Generation Phases

Each template is associated with a phase:

| Phase     | Description                              |
|-----------|------------------------------------------|
| FUNCTION  | Generates function configuration          |
| API       | Generates API gateway definitions         |
| SCHEDULE  | Generates scheduled triggers              |
| FINALIZE  | Final catch-all step (e.g. root modules)  |
| SHARED    | Shared resources used by all functions    |

The generator will call your templates in the correct order based on these phases.

---

## ⚙️ Annotations

Provided in the package `eu.cloudhopper.mc.generator.generic.annotations`:

- `@TemplateRegistration` — top-level annotation to register a generator and its templates
- `@Template` — describes a single template file (name, output format, phase)

---

## 🛠 Developer Notes

- The generator uses Freemarker under the hood
- You do not need to implement the generator SPI manually — just annotate your templates
- Supports rendering Java files (`javaFile = true`) that are compiled into the final project
- Integrates cleanly with the Cloudhopper annotation processor

---

## 🚀 Related Modules

- `deployment-config-generator-api`: defines the SPI and phase model
- `deployment-config-processor`: invokes this generator during annotation processing

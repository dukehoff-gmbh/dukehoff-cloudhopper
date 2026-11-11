# Terraform is a single static binary; taking it from the official image needs no package
# mirrors (apt inside the image build failed to resolve the Ubuntu mirrors on GitHub runners).
FROM hashicorp/terraform:latest AS terraform

FROM maven:3.9.6-eclipse-temurin-21

LABEL maintainer="toni.epple@cloud-hopper.com"
LABEL platform="aws"

# The TCK only runs terraform; the provider and the TCK bindings authenticate through
# environment variables and SDKs, so no cloud CLI is needed.
COPY --from=terraform /bin/terraform /usr/local/bin/terraform

WORKDIR /workspace

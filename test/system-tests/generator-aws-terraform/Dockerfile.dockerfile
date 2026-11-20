# Terraform is a single static binary; taking it from the official image needs no package
# mirrors (apt inside the image build failed to resolve the Ubuntu mirrors on GitHub runners).
ARG TERRAFORM_VERSION=1.17.2
# The AWS CLI v2 is self-contained (its own Python); functions with SnapStart wait for the
# published version with `aws lambda wait` during terraform apply.
ARG AWS_CLI_VERSION=2.39.3

FROM hashicorp/terraform:${TERRAFORM_VERSION} AS terraform
FROM amazon/aws-cli:${AWS_CLI_VERSION} AS awscli

FROM maven:3.9.6-eclipse-temurin-21

LABEL maintainer="toni.epple@cloud-hopper.com"
LABEL platform="aws"

# The provider, the TCK bindings and the AWS CLI authenticate through environment variables.
COPY --from=terraform /bin/terraform /usr/local/bin/terraform
COPY --from=awscli /usr/local/aws-cli /usr/local/aws-cli
RUN ln -s /usr/local/aws-cli/v2/current/bin/aws /usr/local/bin/aws

WORKDIR /workspace

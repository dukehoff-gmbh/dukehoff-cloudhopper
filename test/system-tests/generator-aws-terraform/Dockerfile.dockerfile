FROM maven:3.9.6-eclipse-temurin-21

LABEL maintainer="toni.epple@cloud-hopper.com"
LABEL platform="aws"

RUN apt-get update && apt-get install -y wget unzip gnupg software-properties-common && \
    wget -O- https://apt.releases.hashicorp.com/gpg | gpg --dearmor > /usr/share/keyrings/hashicorp-archive-keyring.gpg && \
    echo "deb [signed-by=/usr/share/keyrings/hashicorp-archive-keyring.gpg] https://apt.releases.hashicorp.com $(lsb_release -cs) main" > /etc/apt/sources.list.d/hashicorp.list && \
    apt-get update && apt-get install -y terraform

RUN apt-get install -y awscli

WORKDIR /workspace

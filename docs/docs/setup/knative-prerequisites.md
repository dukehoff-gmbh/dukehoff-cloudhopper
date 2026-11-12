# Knative Setup Guide

This guide prepares a Kubernetes cluster for Cloudhopper functions built with the
`knative` generator. The generator writes a Helm chart that runs your functions as
one Knative service; see [Knative (`knative`)](../platforms/generator-overview#knative-knative)
for what the chart contains.

:::note

The `knative` target is not part of release 1.1.0. It ships with the next release.

:::

---

## What the cluster needs

| Component | Needed for | Installation |
|---|---|---|
| Knative Serving with a networking layer (Kourier, Istio or Contour) | every project | [Installing Knative Serving](https://knative.dev/docs/install/yaml-install/serving/install-serving-with-yaml/) |
| Knative Eventing | `@ScheduledTrigger` (`PingSource`) | [Installing Knative Eventing](https://knative.dev/docs/install/yaml-install/eventing/install-eventing-with-yaml/) |
| Knative Kafka source and Kafka sink | `@EventTrigger(source = QUEUE)` | `eventing-kafka-controller.yaml`, `eventing-kafka-source.yaml` and `eventing-kafka-sink.yaml` from the [eventing-kafka-broker releases](https://github.com/knative-extensions/eventing-kafka-broker/releases) |
| A Kafka cluster | `@EventTrigger(source = QUEUE)` | any Kafka the cluster can reach, for example with [Strimzi](https://strimzi.io/) |

Cloudhopper is tested with Knative 1.24 and Kafka 3.8.

On your machine you need `docker` (or another image builder), `kubectl` with a
context for the cluster, and [Helm](https://helm.sh/docs/intro/install/) 3 or later.

---

## Build and push the image

The build writes a Dockerfile next to the chart. Build the image from the project
directory and push it to a registry the cluster can pull from:

```bash
mvn clean package -Pknative
docker build -t registry.example.com/my-function:1.0.0 \
  -f target/deployment/knative/shared-resources_dockerfile..dockerfile .
docker push registry.example.com/my-function:1.0.0
```

---

## Install the chart

```bash
helm upgrade --install my-function target/deployment/knative/chart \
  --namespace functions --create-namespace \
  --set image.repository=registry.example.com/my-function \
  --set image.tag=1.0.0 \
  --set 'kafka.bootstrapServers={my-kafka-bootstrap.kafka.svc:9092}'
```

The release name becomes the name of the Knative service. `kafka.bootstrapServers`
is required as soon as a function reads a queue; the chart refuses to render
without it. `values.yaml` in the chart lists all settings: scaling, request
timeout, container resources, environment variables, and the retry and
dead-letter behaviour of queue messages.

For GitOps, render the manifests instead of installing them:

```bash
helm template my-function target/deployment/knative/chart --values my-values.yaml > manifests.yaml
```

---

## Try it locally with kind

The repository's TCK runs against a local [kind](https://kind.sigs.k8s.io/) cluster.
The script that sets it up, `test/knative/kind-setup.sh`, installs Knative
Serving with Kourier, Knative Eventing, the Kafka source and sink and a
single-node Kafka. Services are then reachable at
`http://<release>.<namespace>.localhost:31080`, and Kafka at `localhost:31092`.
Load a locally built image with `kind load docker-image <image> --name cloudhopper`
instead of pushing it.

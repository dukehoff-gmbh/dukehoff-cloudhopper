---
title: Knative
sidebar_label: Knative
---

# Knative

The `knative` generator builds on `springboot-http`: it generates the same
Spring Boot application (see [Spring Boot](springboot.md)) and adds a Helm chart under
`target/deployment/knative/chart` that runs it as one Knative service. The
cluster setup and the installation are described in the
[Knative setup guide](../setup/knative-prerequisites.md).

## What is generated

| Template | Output | Purpose |
|---|---|---|
| `chart.ftl` | `chart/Chart.yaml` | Chart metadata; the chart and app version follow the project version |
| `values.ftl` | `chart/values.yaml` | Image, Kafka bootstrap servers, delivery, object storage, scaling, timeout, resources, environment |
| `service.ftl` | `chart/templates/service.yaml` | Knative `Service`; starts the application with `cloudhopper.schedules.in-process=false` |
| `pingSource.ftl` | `chart/templates/<function>_schedule_pingSource.yaml` | `PingSource` with `cron` and `timezone` that calls `POST /schedules/<function name>` |
| `kafkaSource.ftl` | `chart/templates/<function>_event_kafkaSource.yaml` | For a queue: `KafkaSource` on the topic `<queue>` that delivers to `POST /events/<function name>`, and a `KafkaSink` for the dead-letter topic `<queue>.DLT` |

The profile builds the executable jar `<artifactId>-<version>-knative.jar`,
which the generated Dockerfile packages. The release name of the chart is the
name of the service; the sources and sinks are named `<release>-<function>`.

## Schedules and queues

Schedules and queues reach the service through Knative Eventing, so they work
while the service is scaled to zero:

- **Schedules:** the `PingSource` fires once per schedule, independent of the
  number of replicas, and evaluates `timezone`.
- **Queues:** each queue function has its own consumer group,
  `<kafka.consumerGroupPrefix><function>` (default `cloudhopper-<function>`).
  A message is delivered at least once; when the function fails, it is
  delivered again `delivery.retry` times (default 4) with exponential backoff
  starting at `delivery.backoffDelay` (default `PT1S`), and then written to the
  topic `<queue>.DLT` with the error code in the CloudEvents extensions
  `knativeerrorcode` and `knativeerrordest`. Set `delivery.deadLetterTopic=false`
  to drop failed messages instead. The topic itself must exist, or the Kafka
  cluster must create topics automatically.
- `EventMetadata` gets the CloudEvents attributes of the `KafkaSource`
  (id `partition:<p>/offset:<o>`, the record time). The source does not report
  the delivery attempt, so it is always 0.

## Sending messages

A [`@Queue`](../guides/writing-functions.md#sending-messages) publisher sends to
the Kafka topic of the queue's name, which the `KafkaSource` of the consuming
function delivers. The application needs `spring-boot-starter-kafka`; the
archetype adds it. With `kafka.bootstrapServers` set, the chart starts the
application with `cloudhopper.publisher=kafka` and these servers, so the
application only sends and leaves consuming to the `KafkaSource`s.
`existing = true` sends to a topic of that name in the same Kafka cluster.

## Buckets

A [`@Bucket`](../guides/writing-functions.md#reading-and-writing-buckets) store
reads and writes an object storage through the S3 API, with the S3 client of
`eu.cloudhopper.mc:provider-aws-blobstore`; the archetype adds it without the AWS
Lambda libraries. Any S3-compatible storage works, for example SeaweedFS, Ceph or
the object storage of a cloud, as does S3 itself. The chart creates no buckets:
they must exist, under the name of the `@Bucket` parameter unless
`blobstore.buckets` maps it to another one.

```yaml
blobstore:
  s3Endpoint: http://s3.storage.svc:8333   # empty: S3 of the region
  region: us-east-1
  credentialsSecret: object-storage         # keys access-key-id and secret-access-key
  buckets:
    acme-reports: prod-acme-reports
```

The key comes from the Kubernetes secret `credentialsSecret` in the release's
namespace. The runtime checks the declared `access` of each parameter; what the
key may do is up to the storage.

## Secrets

[Secrets](../guides/writing-functions.md#secrets) come from Kubernetes secrets in
the release's namespace. The Helm value `secrets` lists, per property, the secret
and the key; the chart passes each one with `secretKeyRef` as the environment
variable of the property (`report.api-key` → `REPORT_API_KEY`), so the value is not
part of the chart or the release:

```yaml
secrets:
  - property: report.api-key
    secret: report
    key: api-key
```

Create the secret before the release, e.g.
`kubectl create secret generic report --from-literal=api-key=...`. The pod does not
start while a referenced secret or key is missing. A changed secret reaches the
service with the next revision. Do not set the same property in `env` as well.

## Resources and scaling

`@Function` settings for memory, timeout and instances are not claimed: all
functions run in one container, so these are set for the whole service in
`values.yaml` (`resources`, `timeoutSeconds`, `scaling`).

## Runtime modules

The archetype adds these dependencies to the profile; add them yourself in a project of your own:

| Dependency | Scope | Contains |
|---|---|---|
| `eu.cloudhopper.mc:generator-knative` | `provided`: only the annotation processor uses it | Templates and template registration; builds on `generator-springboot` |
| `eu.cloudhopper.mc:provider-springboot` | compile | The Spring Boot runtime, see [Spring Boot](springboot.md#runtime-modules) |
| `eu.cloudhopper.mc:provider-http` | compile | `HandlerContext` for HTTP services |
| `org.springframework.boot:spring-boot-starter-webmvc` | compile | Spring MVC |
| `org.springframework.boot:spring-boot-starter-kafka` | compile | `@Queue` publishers send to the Kafka topics |
| `eu.cloudhopper.mc:provider-aws-blobstore` | compile, without `aws-lambda-java-core` and `aws-lambda-java-events` | `@Bucket` stores for S3 and S3-compatible storage |

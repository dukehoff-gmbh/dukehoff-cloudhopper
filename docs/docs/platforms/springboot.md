---
title: Spring Boot
sidebar_label: Spring Boot
---

# Spring Boot

The profile `springboot-http` turns the functions into one Spring Boot application
that runs on any server or container platform: every function gets HTTP endpoints,
schedules run in-process or are called from outside, and queues are delivered over
HTTP or from Kafka. The targets [Knative](knative.md) and
[Scaleway](scaleway.md) build on this application.

## What is generated

| Template | Output | Purpose |
|---|---|---|
| `handler.ftl` | Java | `@RestController` per HTTP function |
| `function.ftl` | Java | `@RestController` with `GET`/`POST /functions/<function name>` per function (direct invocation) |
| `event.ftl` | Java | `@RestController` with `POST /events/<function name>` per event-triggered function |
| `eventRoute.ftl` | properties | Where an event-triggered function accepts its events, for tools such as the TCK binding |
| `schedule.ftl` | Java | `@RestController` with `POST /schedules/<function name>` per scheduled function, for an external scheduler |
| `scheduleJob.ftl` | Java | In-process `@Scheduled` job per scheduled function (`cloudhopper.schedules.in-process`) |
| `dockerfile.ftl` | Dockerfile | Container image for the service |

## The application

The project contains the `@SpringBootApplication` class (the archetype creates it
in `src/main/springboot-http`), and the profile builds an executable jar,
`<artifactId>-<version>-springboot-http.jar`, which the Dockerfile packages.
Like on the other targets, a `String` result is returned as JSON (`"pong"`).

The target requires Spring Boot 4 (the archetype uses 4.1). The web starter is
`spring-boot-starter-webmvc`, and Spring MVC serializes with Jackson 3
(`tools.jackson.*`). Jackson annotations (`com.fasterxml.jackson.annotation.*`)
work unchanged. Applications built with Cloudhopper 1.2 or earlier used Spring
Boot 3.2: see the Spring Boot 4 migration guide for changes in your own code.

## Sending messages

A [`@Queue`](../guides/writing-functions.md#sending-messages) publisher delivers
to the queue-triggered functions of the same service: in-process, on a background
thread, retried up to `cloudhopper.events.in-process.max-attempts` times (default
3, pause `cloudhopper.events.in-process.backoff`, default 1 s) and dropped with a
log entry after that; messages do not survive a restart. With Kafka (below), it
sends to the topic of the queue instead and waits for Kafka's acknowledgement;
`cloudhopper.publisher=kafka` sends to Kafka without consuming the topics in the
application, for platforms that deliver them to `POST /events/<function name>`
themselves. A `PublisherFactory` of another runtime module on the classpath, such
as `provider-aws-publisher`, takes precedence over in-process delivery.

## Buckets

A [`@Bucket`](../guides/writing-functions.md#reading-and-writing-buckets) store
keeps each bucket in a directory below `cloudhopper.blobstore.dir` (default
`buckets`, relative to the working directory), named like the bucket, or like
`cloudhopper.bucket.<name>.name` if that is set. This suits local runs and tests;
the declared `access` is checked as on the clouds.

For S3 or S3-compatible storage (for example MinIO), add
`eu.cloudhopper.mc:provider-aws-blobstore`; its store takes precedence over the
directories. It signs with `AWS_ACCESS_KEY_ID` and `AWS_SECRET_ACCESS_KEY` (and
`AWS_SESSION_TOKEN`), in the region `cloudhopper.blobstore.s3.region` or
`AWS_REGION`; `cloudhopper.blobstore.s3.endpoint` sets the endpoint of an
S3-compatible store, which is then addressed by path
(`<endpoint>/<bucket>/<key>`).

## Queues from Kafka

On Spring Boot, `@EventTrigger(source = EventSource.QUEUE, name = "<queue>")`
always has the endpoint `POST /events/<function name>`. To consume the queue
from Kafka instead, add the Kafka starter to the project and set one property; the
function code stays the same:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-kafka</artifactId>
</dependency>
```

Since Spring Boot 4, `spring-kafka` alone is not enough: the Kafka
auto-configuration that provides the connection is part of the starter.

```properties
cloudhopper.events.broker=kafka
spring.kafka.bootstrap-servers=kafka:9092
```

Every queue-triggered function then consumes the topic `<queue>`:

- The consumer group is `cloudhopper-<function id>`, so the replicas of the
  service share the messages and each function sees each message once. Change
  the prefix with `cloudhopper.events.kafka.group-id-prefix`.
- A message is committed after the function returned. When the function throws,
  it is delivered again after `cloudhopper.events.kafka.backoff` (default `1s`),
  up to `cloudhopper.events.kafka.max-attempts` times (default `5`), and then
  published to the topic `<queue>.DLT`.
- A new consumer group starts with the oldest message in the topic, as a new
  queue subscription does on the cloud targets.
- `EventMetadata` gets the CloudEvents attributes of the Kafka binding
  (`ce_id`, `ce_source`, `ce_time` headers); without them the id is
  `<topic>-<partition>-<offset>`, which stays the same when a message is
  delivered again, and the time is the record timestamp. Other headers become
  the attributes.

Manage the versions with the Spring Boot BOM (`spring-boot-dependencies` or
`spring-boot-starter-parent`). Without it, Maven may pick the SLF4J 1.7 that
`kafka-clients` declares, and Spring Boot does not start.

The endpoint follows the CloudEvents HTTP binding (`ce-id`, `ce-source`,
`ce-time` headers), so a Knative `Trigger` or another CloudEvents sender can
deliver to `POST /events/<function name>` without Kafka on the classpath.

## Runtime modules

The archetype adds these dependencies to the profile; add them yourself in a project of your own:

| Dependency | Scope | Contains |
|---|---|---|
| `eu.cloudhopper.mc:generator-springboot` | `provided`: only the annotation processor uses it | Templates and template registration |
| `eu.cloudhopper.mc:provider-springboot` | compile | Base classes of the generated controllers (`SpringBootRequestHandler`, `SpringBootEventHandler`), the publishers (in-process, Kafka) and the Kafka delivery (`cloudhopper.events.broker=kafka`) |
| `eu.cloudhopper.mc:provider-http` | compile | `HandlerContext` for HTTP services |
| `org.springframework.boot:spring-boot-starter-webmvc` | compile | Spring MVC |
| `org.springframework.boot:spring-boot-starter-kafka` | compile, optional | Only for queues from Kafka |

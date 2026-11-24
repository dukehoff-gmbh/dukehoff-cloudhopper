#!/usr/bin/env bash
# Creates a local kind cluster with Knative Serving (Kourier), Knative Eventing,
# the Knative Kafka source and sink, a single-node Kafka, an S3-compatible object storage
# (SeaweedFS) and a PostgreSQL database. Used by the knative TCK.
#
#   services: http://<service>.<namespace>.localhost:31080 (*.localhost resolves to the loopback address)
#   Kafka:    kafka.kafka.svc:9092 in the cluster, localhost:31092 from the host
#   S3:       http://s3.blobstore.svc:8333 in the cluster, key cloudhopper-tck / cloudhopper-tck-secret
#   Postgres: jdbc:postgresql://postgres.keyvalue.svc:5432/cloudhopper in the cluster, user cloudhopper-tck / cloudhopper-tck-secret
set -euo pipefail

CLUSTER=${CLUSTER:-cloudhopper}
KNATIVE=${KNATIVE_VERSION:-knative-v1.24.0}
KAFKA_IMAGE=${KAFKA_IMAGE:-apache/kafka:3.8.0}
SEAWEEDFS_IMAGE=${SEAWEEDFS_IMAGE:-chrislusf/seaweedfs:4.50}
POSTGRES_IMAGE=${POSTGRES_IMAGE:-postgres:17-alpine}
GH=https://github.com
wait_ns() { kubectl wait deployment --all -n "$1" --for=condition=Available --timeout=300s; }

if ! kind get clusters | grep -qx "$CLUSTER"; then
  kind create cluster --name "$CLUSTER" --config - <<KIND
kind: Cluster
apiVersion: kind.x-k8s.io/v1alpha4
nodes:
- role: control-plane
  extraPortMappings:
  - containerPort: 31080
    hostPort: 31080
  - containerPort: 31092
    hostPort: 31092
KIND
fi
kubectl config use-context "kind-$CLUSTER"

# Serving with Kourier
kubectl apply -f $GH/knative/serving/releases/download/$KNATIVE/serving-crds.yaml
kubectl apply -f $GH/knative/serving/releases/download/$KNATIVE/serving-core.yaml
kubectl apply -f $GH/knative-extensions/net-kourier/releases/download/$KNATIVE/kourier.yaml
kubectl patch configmap/config-network -n knative-serving --type merge \
  -p '{"data":{"ingress-class":"kourier.ingress.networking.knative.dev"}}'
kubectl patch configmap/config-domain -n knative-serving --type merge \
  -p '{"data":{"localhost":""}}'
wait_ns knative-serving
wait_ns kourier-system
kubectl patch service/kourier -n kourier-system --type merge \
  -p '{"spec":{"type":"NodePort","ports":[{"name":"http2","port":80,"targetPort":8080,"nodePort":31080}]}}'

# Eventing core, the Kafka source and the Kafka sink (dead-letter topics)
kubectl apply -f $GH/knative/eventing/releases/download/$KNATIVE/eventing-crds.yaml
kubectl apply -f $GH/knative/eventing/releases/download/$KNATIVE/eventing-core.yaml
wait_ns knative-eventing
kubectl apply -f $GH/knative-extensions/eventing-kafka-broker/releases/download/$KNATIVE/eventing-kafka-controller.yaml
kubectl apply -f $GH/knative-extensions/eventing-kafka-broker/releases/download/$KNATIVE/eventing-kafka-source.yaml
kubectl apply -f $GH/knative-extensions/eventing-kafka-broker/releases/download/$KNATIVE/eventing-kafka-sink.yaml
wait_ns knative-eventing

# Single-node Kafka (KRaft)
kubectl create namespace kafka --dry-run=client -o yaml | kubectl apply -f -
kubectl apply -n kafka -f - <<K
apiVersion: apps/v1
kind: Deployment
metadata: {name: kafka}
spec:
  selector: {matchLabels: {app: kafka}}
  template:
    metadata: {labels: {app: kafka}}
    spec:
      enableServiceLinks: false
      containers:
      - name: kafka
        image: $KAFKA_IMAGE
        env:
        - {name: KAFKA_NODE_ID, value: "1"}
        - {name: KAFKA_PROCESS_ROLES, value: "broker,controller"}
        - {name: KAFKA_LISTENERS, value: "PLAINTEXT://:9092,EXTERNAL://:29092,CONTROLLER://:9093"}
        - {name: KAFKA_ADVERTISED_LISTENERS, value: "PLAINTEXT://kafka.kafka.svc:9092,EXTERNAL://localhost:31092"}
        - {name: KAFKA_CONTROLLER_LISTENER_NAMES, value: "CONTROLLER"}
        - {name: KAFKA_LISTENER_SECURITY_PROTOCOL_MAP, value: "CONTROLLER:PLAINTEXT,PLAINTEXT:PLAINTEXT,EXTERNAL:PLAINTEXT"}
        - {name: KAFKA_CONTROLLER_QUORUM_VOTERS, value: "1@localhost:9093"}
        - {name: KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR, value: "1"}
        - {name: KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR, value: "1"}
        - {name: KAFKA_TRANSACTION_STATE_LOG_MIN_ISR, value: "1"}
        - {name: KAFKA_AUTO_CREATE_TOPICS_ENABLE, value: "true"}
        ports: [{containerPort: 9092}, {containerPort: 29092}]
---
apiVersion: v1
kind: Service
metadata: {name: kafka}
spec:
  selector: {app: kafka}
  ports: [{name: internal, port: 9092}]
---
apiVersion: v1
kind: Service
metadata: {name: kafka-external}
spec:
  type: NodePort
  selector: {app: kafka}
  ports: [{name: external, port: 29092, nodePort: 31092}]
K
wait_ns kafka

# S3-compatible object storage for @Bucket; a fixed key, only reachable in the cluster
kubectl create namespace blobstore --dry-run=client -o yaml | kubectl apply -f -
kubectl apply -n blobstore -f - <<S3
apiVersion: v1
kind: ConfigMap
metadata: {name: s3-identities}
data:
  s3.json: |
    {"identities": [{"name": "tck",
      "credentials": [{"accessKey": "cloudhopper-tck", "secretKey": "cloudhopper-tck-secret"}],
      "actions": ["Admin", "Read", "Write", "List", "Tagging"]}]}
---
apiVersion: apps/v1
kind: Deployment
metadata: {name: s3}
spec:
  selector: {matchLabels: {app: s3}}
  template:
    metadata: {labels: {app: s3}}
    spec:
      # the service s3 would set S3_PORT, which SeaweedFS reads as its flag s3.port
      enableServiceLinks: false
      containers:
      - name: seaweedfs
        image: $SEAWEEDFS_IMAGE
        args: ["server", "-dir=/data", "-s3", "-s3.config=/etc/seaweedfs/s3.json"]
        ports: [{containerPort: 8333}]
        readinessProbe: {tcpSocket: {port: 8333}, periodSeconds: 2}
        volumeMounts:
        - {name: identities, mountPath: /etc/seaweedfs}
        - {name: data, mountPath: /data}
      volumes:
      - {name: identities, configMap: {name: s3-identities}}
      - {name: data, emptyDir: {}}
---
apiVersion: v1
kind: Service
metadata: {name: s3}
spec:
  selector: {app: s3}
  ports: [{name: s3, port: 8333}]
S3
wait_ns blobstore
# PostgreSQL for @Table; a fixed password, only reachable in the cluster
kubectl create namespace keyvalue --dry-run=client -o yaml | kubectl apply -f -
kubectl apply -n keyvalue -f - <<PG
apiVersion: apps/v1
kind: Deployment
metadata: {name: postgres}
spec:
  selector: {matchLabels: {app: postgres}}
  template:
    metadata: {labels: {app: postgres}}
    spec:
      enableServiceLinks: false
      containers:
      - name: postgres
        image: $POSTGRES_IMAGE
        env:
        - {name: POSTGRES_USER, value: cloudhopper-tck}
        - {name: POSTGRES_PASSWORD, value: cloudhopper-tck-secret}
        - {name: POSTGRES_DB, value: cloudhopper}
        ports: [{containerPort: 5432}]
        readinessProbe: {exec: {command: [pg_isready, -U, cloudhopper-tck, -d, cloudhopper]}, periodSeconds: 2}
        volumeMounts:
        - {name: data, mountPath: /var/lib/postgresql/data}
      volumes:
      - {name: data, emptyDir: {}}
---
apiVersion: v1
kind: Service
metadata: {name: postgres}
spec:
  selector: {app: postgres}
  ports: [{name: postgres, port: 5432}]
PG
wait_ns keyvalue
echo "cluster $CLUSTER ready"

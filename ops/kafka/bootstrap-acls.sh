#!/usr/bin/env bash
set -euo pipefail
broker=kafka:29092
config=/run/secrets/kafka-admin.properties
for topic in raw-logs incoming-alerts raw-logs-dlq incoming-alerts-dlq; do
  partitions=3
  [[ "$topic" == *-dlq ]] && partitions=1
  /opt/kafka/bin/kafka-topics.sh --bootstrap-server "$broker" --command-config "$config" --create --if-not-exists --topic "$topic" --partitions "$partitions" --replication-factor 1
  /opt/kafka/bin/kafka-acls.sh --bootstrap-server "$broker" --command-config "$config" --add --allow-principal 'User:CN=roottrace-backend' --operation Read --operation Write --operation Describe --topic "$topic"
done
for group in roottrace-log-processor roottrace-alert-processor; do
  /opt/kafka/bin/kafka-acls.sh --bootstrap-server "$broker" --command-config "$config" --add --allow-principal 'User:CN=roottrace-backend' --operation Read --operation Describe --group "$group"
done
/opt/kafka/bin/kafka-acls.sh --bootstrap-server "$broker" --command-config "$config" --add --allow-principal 'User:CN=roottrace-backend' --operation IdempotentWrite --cluster

#!/usr/bin/env bash
set -euo pipefail
cluster_id=$(tr -d '\r\n' < /run/secrets/kafka-cluster-id)
metadata=/var/lib/kafka/data/meta.properties
if [[ -f "$metadata" ]]; then
  existing_id=$(sed -n 's/^cluster.id=//p' "$metadata" | tr -d '\r\n')
  if [[ "$existing_id" != "$cluster_id" ]]; then
    echo 'Configured cluster ID differs from existing metadata. Refusing to modify broker storage.' >&2
    exit 1
  fi
elif [[ -n "$(find /var/lib/kafka/data -mindepth 1 -maxdepth 1 -print -quit)" ]]; then
  echo 'Non-empty broker storage has no root metadata. Refusing to initialize unknown data.' >&2
  exit 1
fi
/opt/kafka/bin/kafka-storage.sh format --ignore-formatted --cluster-id "$cluster_id" --config /run/secrets/kafka-broker.properties
exec /opt/kafka/bin/kafka-server-start.sh /run/secrets/kafka-broker.properties

# Telemetry streaming stack

Local Docker stack for the IoT telemetry pipeline (issue #43). The SmartHome hub runs on the host
(not in Docker) and publishes every recorded reading to Redpanda, a Kafka-API-compatible broker.
Kafka Connect then sinks the topic into Elasticsearch, which you browse in Kibana:

```
hub (host) ──▶ Redpanda ──▶ Kafka Connect ──▶ Elasticsearch ──▶ Kibana
              telemetry.readings   ES sink
```

The hub writes no code for the sink half — Connect moves messages off the topic on its own.

## Run

```
docker compose -f infrastructure/streaming/docker-compose.yml up -d
```

First start pulls images and installs the Elasticsearch sink connector, so give it a minute. The
`connect-setup` container registers the sink and exits once Connect is up.

| Service | Address | Purpose |
| --- | --- | --- |
| Redpanda (Kafka API) | `localhost:19092` | the hub connects here (override with `SMARTHOME_KAFKA_BOOTSTRAP=host:port`) |
| Redpanda Console | http://localhost:8088 | topic browser |
| Elasticsearch | http://localhost:9200 | indexed readings |
| Kibana | http://localhost:5601 | dashboards over the readings |
| Kafka Connect | http://localhost:8083 | connector REST API |

## Verify telemetry is flowing

Start the hub, let a sensor node publish (or send an MQTT reading), then check each hop:

```
# 1. On the topic
docker exec -it smarthome-redpanda rpk topic consume telemetry.readings

# 2. The sink is running (state should be RUNNING)
curl -s http://localhost:8083/connectors/telemetry-elasticsearch-sink/status

# 3. Indexed in Elasticsearch
curl -s 'http://localhost:9200/telemetry.readings/_search?size=1&pretty'
```

In Kibana, create a data view on the `telemetry.readings` index (time field `timestamp`) to chart
the readings.

## Stop

```
docker compose -f infrastructure/streaming/docker-compose.yml down       # keep data
docker compose -f infrastructure/streaming/docker-compose.yml down -v    # also wipe volumes
```

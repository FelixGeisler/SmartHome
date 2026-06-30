# Telemetry streaming stack

Local Docker stack for the IoT telemetry pipeline (issue #43). The SmartHome hub runs on the host
(not in Docker) and publishes every recorded reading to Redpanda, a Kafka-API-compatible broker.

## Run

```
docker compose -f deploy/docker-compose.yml up -d
```

- **Redpanda** (Kafka API): `localhost:19092` — the hub connects here. This is the default; override
  with `SMARTHOME_KAFKA_BOOTSTRAP=host:port` if needed.
- **Redpanda Console** (topic browser): http://localhost:8088

## Verify telemetry is flowing

Start the hub, let a sensor node publish (or send an MQTT reading), then tail the topic:

```
docker exec -it smarthome-redpanda rpk topic consume telemetry.readings
```

You should see one JSON message per recorded reading.

## Stop

```
docker compose -f deploy/docker-compose.yml down       # keep data
docker compose -f deploy/docker-compose.yml down -v    # also wipe volumes
```

Elasticsearch, Kibana, and the Connect → Elasticsearch sink are added in the next slice.

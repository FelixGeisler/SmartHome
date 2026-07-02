# SmartHome

[![CI](https://github.com/FelixGeisler/SmartHome/actions/workflows/ci.yml/badge.svg)](https://github.com/FelixGeisler/SmartHome/actions/workflows/ci.yml)
[![Docs](https://github.com/FelixGeisler/SmartHome/actions/workflows/docs.yml/badge.svg)](https://felixgeisler.github.io/SmartHome/)

A self-hosted smart-home hub. It brings devices from different ecosystems into a single dashboard, records their sensor
history, and includes an AI assistant that can query and control
everything.

## Supported devices

- **Philips Hue** lights, through a Hue bridge. On/off, plus brightness, color,
  and color temperature where the bulb supports them.
- **Shelly** plugs, over HTTP on the local network. On/off.
- **MQTT sensor nodes** reporting temperature, humidity, pressure, or CO₂.

Each integration is a single adapter that maps its ecosystem onto a shared,
capability-based device model. The dashboard, the REST API, and the assistant
all work on that model, so to support a new ecosystem, you only need to add a new adapter.

## Quick start

```sh
cd hub
./mvnw clean verify
java -jar target/smarthome-*.jar
```

Dashboard and API run on <http://localhost:8080>.

Devices, MQTT sensors, and the assistant are all set up in the UI, as described
in the [user guide](https://felixgeisler.github.io/SmartHome/guide/). Connections are
persisted, so the hub restores them after a restart. Sensor history charts
require the streaming stack in
[infrastructure/streaming](infrastructure/streaming/README.md).

## Documentation

The architecture documentation and the user guide are published at
[felixgeisler.github.io/SmartHome](https://felixgeisler.github.io/SmartHome/).

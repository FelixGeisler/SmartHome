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

The hub ships as a single container image that bundles its own Java runtime, so nothing else has to
be installed. With Docker or Podman, from the repository root:

```sh
docker compose up -d
```

The dashboard and API are then on <http://localhost:8080>. The image is published for both `amd64`
and `arm64`, so the same command works on a PC, a home server, or a Raspberry Pi.

The container uses `restart: unless-stopped`, so it comes back after a crash and after a host reboot
(with Docker, or with rootless Podman once `systemctl --user enable podman-restart.service` is set),
keeping the hub running as an always-on service. To update to a newer release, pull and recreate:

```sh
docker compose pull && docker compose up -d
```

Devices, MQTT sensors, and the assistant are all set up in the UI, as described in the
[user guide](https://felixgeisler.github.io/SmartHome/guide/). Connections are persisted in the
`smarthome-data` volume, so the hub restores them after a restart or an image upgrade.

### Build from source

To run against local changes instead of the published image, build the jar with the Maven wrapper
(this also builds the bundled UI):

```sh
cd hub
./mvnw clean verify
java -jar target/smarthome-*.jar
```

## Documentation

The architecture documentation and the user guide are published at
[felixgeisler.github.io/SmartHome](https://felixgeisler.github.io/SmartHome/).

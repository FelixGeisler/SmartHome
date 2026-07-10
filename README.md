# SmartHome

A self-hosted smart-home hub: one dashboard for devices from different ecosystems, on your own hardware.

[![CI](https://github.com/FelixGeisler/SmartHome/actions/workflows/ci.yml/badge.svg)](https://github.com/FelixGeisler/SmartHome/actions/workflows/ci.yml)
[![Docs](https://github.com/FelixGeisler/SmartHome/actions/workflows/docs.yml/badge.svg)](https://felixgeisler.github.io/SmartHome/)

It runs as a single container on your own hardware, with no vendor apps and no required cloud account, so your devices and their data stay on your home network.

![The SmartHome dashboard: device cards with their controls](docs/images/dashboard.png)

## Features

- **One dashboard across ecosystems.** Every device on a layout you arrange and the hub remembers.
- **Rooms and floors.** Group your devices on a floor plan.
- **Automations.** Act on sensor thresholds and schedules.
- **AI assistant.** Query and control your home in plain language, with your own Anthropic key.
- **Sensor history.** Chart each sensor's readings over time.
- **Self-hosted and extensible.** One container on your hardware, and a new ecosystem is one adapter.

![Devices grouped by room on a floor plan](docs/images/rooms.png)

## Supported devices

- **Philips Hue** lights, through a bridge: on/off, brightness, color, and color temperature.
- **Shelly** plugs: on/off, with power metering.
- **Homematic** devices, through a CCU: switches and dimmers, plus climate and contact sensors.
- **Solakon** solar inverters: power and energy readings over Modbus.
- **MQTT sensor nodes**: temperature, humidity, pressure, CO2, and air quality.

## Quick start

The hub ships as a single container image that bundles its own Java runtime. With Docker or Podman,
from the repository root:

```sh
docker compose up -d
```

The dashboard and API are then on <http://localhost:8080>. The image is published for both `amd64`
and `arm64`, so the same command works on a PC, a home server, or a Raspberry Pi, and the container
restarts on boot. Building from source and first-time setup are in the
[user guide](https://felixgeisler.github.io/SmartHome/guide/).

## Documentation

The user guide and architecture docs are published at [felixgeisler.github.io/SmartHome](https://felixgeisler.github.io/SmartHome/). The [user guide](https://felixgeisler.github.io/SmartHome/guide/) covers building and running the hub, adding devices, and setting up rooms, automations, and the assistant.

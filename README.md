# SmartHome

> A self-hosted smart-home hub that unifies devices behind one neutral model, streams their
> telemetry in real time, and lets you run your home in plain language through a built-in AI assistant.

SmartHome brings devices from different ecosystems — MQTT sensor nodes, Philips Hue, Shelly plugs —
under one roof, continuously records what their sensors report, and makes all of it visible and
controllable from a single web app. It's built as an open, long-lived platform rather than a one-off:
a small core with well-defined seams, so new device types and protocols slot in without rewrites.

**📖 [Live architecture docs (arc42) →](https://felixgeisler.github.io/SmartHome/)**

## What makes it interesting

- **One neutral device model, pluggable adapters.** Each integration translates its own protocol
  into a single capability-based model, so the UI, control, and AI only ever speak one language.
  Lights expose rich capabilities — on/off, brightness, and color as CIE xy / Kelvin / %.
- **Telemetry is streamed, not just stored.** Raspberry Pi nodes read real I²C sensors (temperature,
  humidity, pressure, CO₂) and publish over MQTT. The hub records each reading and, decoupled through
  a domain event, tees it onto a **Kafka** topic → **Kafka Connect** → **Elasticsearch** — no
  consumer code in the hub, so new sinks are pure configuration.
- **History is a queryable read-model.** The dashboard turns it into live per-sensor **d3.js** charts
  that update as readings arrive; Kibana sits over the same index for ad-hoc analysis.
- **The AI assistant drives the real system.** A floating chat (Claude, tool-use) calls the *same*
  services the UI does — so it can answer *"how's the air in the living room?"*, act on *"turn the
  table lamp blue"*, and flag issues proactively (*"CO₂ is high — open a window"*).
- **Connections heal themselves.** MQTT, Hue pairing, and the assistant key persist and auto-restore
  across restarts, so the hub comes back exactly as you left it.

## Architecture at a glance

```
 Raspberry Pi sensors ──MQTT──▶ Mosquitto ──▶  ┌─────────────────────┐ ──▶ Kafka ▶ Connect ▶ Elasticsearch ─┐
                                               │  Hub (Spring Boot)  │                                        ├─▶ Dashboard (React · d3) / Kibana
        Hue Bridge · Shelly plugs ◀──HTTP/LAN──│  + bundled React UI │ ◀── history queries ───────────────────┘
                                               └──────────┬──────────┘
                                                          └──▶ Anthropic Claude API  (AI assistant, tool-use)
```

The hub is a single Spring Boot jar that serves the React dashboard **and** the REST API from one
origin — no CORS, one deploy. Everything else (the streaming stack, the sensor nodes) runs around it.
The full picture, decisions, and ADRs live in the **[architecture docs](https://felixgeisler.github.io/SmartHome/)**.

## Repository layout

| Path | What |
| --- | --- |
| **`hub/`** | The Spring Boot hub — server + the bundled React UI, built into one jar (`hub/src`, `hub/frontend`). |
| **`infrastructure/streaming/`** | The local streaming/analytics stack (Redpanda/Kafka, Kafka Connect, Elasticsearch, Kibana). |
| **`infrastructure/sensor-node/`** | The Python sensor node for the Raspberry Pis, plus its Mosquitto broker config. |
| **`docs/`** | The arc42 architecture docs and user guide (Antora). |

## Tech stack

**Java 26** · **Spring Boot 4** · Spring Data JPA · Flyway · Eclipse Paho (MQTT) · Spring Kafka ·
Elasticsearch · **Vite + React + TypeScript** · d3.js · Anthropic Claude (tool-use) · Raspberry Pi (Python).
Backend built with Maven; the streaming stack runs locally via Podman/Docker Compose.

## Getting started

Install the frontend deps once, then run the backend and the Vite dev server as two processes:

```sh
npm --prefix hub/frontend install    # once (frontend deps)
```

```sh
# terminal 1 — backend API on :8080 (skip the bundled build; Vite serves the UI in dev)
cd hub && ./mvnw spring-boot:run -Dskip.frontend=true   # Windows: .\mvnw.cmd
```

```sh
# terminal 2 — Vite dev server on :5173, proxies /api to the backend
npm --prefix hub/frontend run dev
```

Open <http://localhost:5173> — frontend edits hot-reload, backend edits need a rerun. For the live
telemetry pipeline (Kafka → Elasticsearch → Kibana), bring up the stack in
[`infrastructure/streaming/`](infrastructure/streaming/README.md).

### Run it packaged, exactly as deployed

```sh
cd hub
./mvnw clean verify
java -jar target/smarthome-*.jar
```

UI and API together on <http://localhost:8080>.

## Quality

One gate for everything: `cd hub && ./mvnw clean verify` runs the backend tests and analyzers
(Checkstyle, PMD, SpotBugs, JaCoCo ≥ 70 %) plus the frontend's ESLint, Vitest, and production build —
which the jar then serves at `/`. Iterating on the backend only? `-Dskip.frontend=true` skips the
npm lifecycle.

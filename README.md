# SmartHome

A home automation application built with Spring Boot, with a React device dashboard
served from the same jar.

## Repository layout

- **`hub/`** — the Spring Boot hub: server + the bundled React UI, built into one jar
  (`hub/src`, `hub/frontend`, `hub/pom.xml`).
- **`infrastructure/`** — everything the hub runs against:
  - **`streaming/`** — the local streaming/analytics stack (Redpanda/Kafka, Kafka Connect,
    Elasticsearch, Kibana).
  - **`sensor-node/`** — the Python sensor node that runs on the Raspberry Pis, plus its
    Mosquitto broker config (`mosquitto.conf`).
- **`docs/`** — the arc42 architecture docs and user guide (Antora).

## Tech Stack

- **Java 26**
- **Spring Boot 4**
- **Maven**
- **Vite + React + TypeScript** (`hub/frontend/` — see its [README](hub/frontend/README.md))

## Run (development)

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

Open <http://localhost:5173> — frontend edits hot-reload, backend edits need a rerun.

## Run (packaged)

```sh
cd hub
./mvnw clean verify
java -jar target/smarthome-*.jar
```

UI and API together on <http://localhost:8080>, exactly as deployed.

## Build

```sh
cd hub && ./mvnw clean verify
```

One gate for everything: backend tests and analysers plus the frontend's ESLint,
Vitest, and production build, which the jar then serves at `/`. Iterating on the
backend only? `-Dskip.frontend=true` skips the npm lifecycle.

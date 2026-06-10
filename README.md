# SmartHome

A home automation application built with Spring Boot, with a React device dashboard
served from the same jar.

## Tech Stack

- **Java 26**
- **Spring Boot 4.0.6**
- **Maven**
- **Vite + React + TypeScript** (`frontend/` — see its [README](frontend/README.md))

## Run (development)

```sh
npm install                    # repo root (docs + dev tooling)
npm --prefix frontend install  # once (frontend deps)
npm run dev
```

Starts the backend (port 8080) and the Vite dev server together; open
<http://localhost:5173> — frontend edits hot-reload, backend edits need a rerun.

## Run (packaged)

```sh
./mvnw clean verify
java -jar target/smarthome-0.0.1-SNAPSHOT.jar
```

UI and API together on <http://localhost:8080>, exactly as deployed.

## Build

```sh
./mvnw clean verify
```

One gate for everything: backend tests and analysers plus the frontend's ESLint,
Vitest, and production build, which the jar then serves at `/`. Iterating on the
backend only? `-Dskip.frontend=true` skips the npm lifecycle.

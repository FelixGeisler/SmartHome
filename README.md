# SmartHome

A home automation application built with Spring Boot, with a React device dashboard
served from the same jar.

## Tech Stack

- **Java 26**
- **Spring Boot 4.0.6**
- **Maven**
- **Vite + React + TypeScript** (`frontend/` — see its [README](frontend/README.md))

## Build

```sh
./mvnw clean verify
```

One gate for everything: backend tests and analysers plus the frontend's ESLint,
Vitest, and production build, which the jar then serves at `/`. Iterating on the
backend only? `-Dskip.frontend=true` skips the npm lifecycle.

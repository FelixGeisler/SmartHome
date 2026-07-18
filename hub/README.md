# Hub

The Spring Boot server and the bundled React UI.

- `src/` - Java server, base package `org.felixgeisler.smarthome`
- `frontend/` - React UI, built into the jar by the frontend-maven-plugin (see [frontend/README.md](frontend/README.md))

Run locally with `./mvnw spring-boot:run`. The full gate is `./mvnw clean verify`; add
`-Dskip.frontend=true` for backend-only iteration.

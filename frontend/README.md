# SmartHome frontend

The device dashboard: a Vite + React + TypeScript single-page app that lists the hub's
registered devices and toggles them on or off through the REST API.

## Development

```sh
npm install
npm run dev
```

The dev server proxies `/api` to the Spring Boot app on `http://localhost:8080`
(see `vite.config.ts`), so start the backend first:

```sh
.\mvnw.cmd spring-boot:run   # from the repository root
```

## Quality gate

```sh
npm run lint   # ESLint
npm test       # Vitest (one-shot; use npm run test:watch while developing)
npm run build  # type-check + production bundle in dist/
```

The Maven build runs all three and bundles `dist/` into the Spring Boot jar, so
`.\mvnw.cmd clean verify` from the repository root covers the frontend too.

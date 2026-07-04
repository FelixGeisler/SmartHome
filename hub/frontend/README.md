# SmartHome frontend

The device dashboard: a Vite + React + TypeScript single-page app that registers,
controls, and charts devices through the REST API, kept live by the hub's
server-sent event stream.

## Development

Run the backend and this Vite dev server as two processes. Install the frontend deps once with
`npm --prefix hub/frontend install` from the repository root (see the root README).

Start the backend first, from `hub/`.
The skip flag matters: `spring-boot:run` forks the build lifecycle through the
phase that runs `npm ci`, which wipes `node_modules` on every backend start, and
fails outright if the Vite dev server is holding files open:

```sh
cd hub
.\mvnw.cmd spring-boot:run "-Dskip.frontend=true"
```

Then run the dev server, which serves the dashboard on `http://localhost:5173` and proxies
`/api` to the Spring Boot app on `http://localhost:8080` (see `vite.config.ts`). Careful with
the working directory: the repository root has its own `package.json` (the docs toolchain), so
bare `npm` commands must run from `hub/frontend/`, or use the prefix form from the root:

```sh
npm --prefix hub/frontend run dev
```

## Quality gate

Run from `hub/frontend/`:

```sh
npm run lint   # ESLint
npm test       # Vitest (one-shot; use npm run test:watch while developing)
npm run build  # type-check + production bundle in dist/
```

The Maven build runs all three and bundles `dist/` into the Spring Boot jar, so
`.\mvnw.cmd clean verify` from `hub/` covers the frontend too.

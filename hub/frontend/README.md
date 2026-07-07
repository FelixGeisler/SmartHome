# SmartHome frontend

The device dashboard: a Vite + React + TypeScript single-page app that registers,
controls, and charts devices, and arranges them on a room floor plan, through the
REST API, kept live by the hub's server-sent event stream.

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

## Rooms floor plan

The Rooms view arranges devices as icons inside room boxes. The arrangement (each room box's grid
position and size, and each device's spot within its room) is saved as a single JSON blob in the hub
settings, the same way the dashboard layout is. It is UI geometry with no device-domain meaning, so
it never touches the device or room entities; room membership stays the domain fact on the device.
Device positions are stored as fractions (0..1) of the room content rect, so an icon keeps its
relative spot when a room box is resized, and each device gets one explicit stored position so
removing one device never shifts where another sits. The blob is capped at the settings column width
(4096 characters): positions are rounded to three decimals to keep it compact, and an over-large
layout is rejected with a 422 rather than truncated.

## Device health and management

The hub tracks whether each device is reachable. Once a minute it probes every command device
through its adapter and marks a reporting sensor node offline when no reading has arrived within the
last ten minutes. The reachable state rides the same event stream as everything else and is pushed
only when it flips, so a steadily online or offline device is silent. An offline device wears an
"Offline" badge on its dashboard card and in the manage-devices list.

Each sensor reading on a card also shows how long ago it arrived, and a reading past the same
ten-minute window is dimmed so a value that may no longer be current stands out. The age label
advances on a timer, so a device that falls silent visibly ages even while nothing is being pushed.

The Configuration view lists every registered device and lets you rename one or remove it. A delete
asks for a second, confirming click first; a sensor node that is still publishing reappears on its
next reading.

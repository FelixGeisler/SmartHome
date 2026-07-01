---
description: Java coding conventions for SmartHome, applied when editing Java sources
globs: hub/src/**/*.java
---

# Java conventions

Applies to all Java under `hub/src/`. These mirror and reinforce the rules in `CLAUDE.md`.

- **Google Java Style** (`google_checks.xml`): 2-space indentation, no wildcard imports,
  ~100-column lines. CheckStyle enforces it — match the surrounding code.
- **Logging through SLF4J only** — `private static final Logger log = LoggerFactory.getLogger(...)`.
  No `System.out` / `System.err`, no `printStackTrace()`.
- **Exception handling** — catch the most specific type; never an empty catch. Log with context
  (the relevant ids/values) and rethrow or handle deliberately — a swallowed exception hides the
  failure.
- **Tests** — JUnit 5, AAA (arrange/act/assert), one behavior per test, with a `@DisplayName`
  describing the verified behavior on every test.

# SmartHome

Spring Boot smart-home hub.

## Stack & layout

- Spring Boot with Spring Data JPA, built with Maven — always use the wrapper
  (`.\mvnw.cmd` / `./mvnw`). Exact versions are in `pom.xml`.
- Base package `org.felixgeisler.smarthome`. One feature per sub-package
  (`device`, `room`, …): model + repository + service + controller live together.

## Architecture conventions

- **Layering:** `Controller` → `Service` → `Repository`. Controllers do no business logic;
  services hold it; repositories handle persistence only.
- **Constructor injection only** — never field injection (`@Autowired` on fields). Keeps
  classes unit-testable without Spring.
- **Repositories are Spring Data interfaces** — e.g.
  `interface DeviceRepository extends JpaRepository<Device, Long>`. Spring generates the
  implementation; don't write one yourself or annotate it `@Repository`. You still mock the
  interface in tests, so it stays swappable.
- Return `Optional<T>` for "may not exist" lookups; never return `null` from a service method.

## Coding standards

- Follow **Google Java Style** (`google_checks.xml`) — CheckStyle enforces it.
- Javadoc on public types and non-trivial public methods, with `@param`/`@return`.

## Forbidden patterns

- **No secrets in source** — API keys, passwords, tokens come from environment variables, never
  committed. The quality tools don't scan for this, so `.gitignore` + externalized config is the
  real backstop.
- **Never suppress a warning** (`@SuppressWarnings`, `// NOPMD`, `// CHECKSTYLE:OFF`) without a
  comment saying exactly why — a silenced gate is worse than no gate.
- **Logging goes through SLF4J:** `private static final Logger log = LoggerFactory.getLogger(...)`.
  No `System.out`/`err`, no `e.printStackTrace()`, no empty `catch` (log it or rethrow — a
  swallowed exception hides the failure). PMD/CheckStyle enforce these once wired; the point here
  is getting it right on the first pass.

## Workflow rules

- After any code change, run `./mvnw test` before reporting done — a refactor that breaks a test
  isn't a finished refactor.
- Before committing, the gate is `./mvnw clean verify` (compile + tests + CheckStyle/PMD/SpotBugs).
  Code passes it first.
- Small, meaningful commits — one logical change each.

## Testing

- **Every test asserts something meaningful.** Coverage proves a line ran, not that behavior was
  verified — an assertion-free test is coverage theater. No empty test bodies.
- **AAA** — arrange, act, assert. One behavior per test, exercised through the public API and
  named for what it verifies.
- **Unit-test services as plain objects**, with Mockito mocks for their repositories and
  collaborators — which is exactly why constructor injection and interface repositories are in the
  rules above. Reach for the framework only when needed: `@WebMvcTest` for controllers,
  `@DataJpaTest` for repositories, `@SpringBootTest` only when a test truly needs the whole context.
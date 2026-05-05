# AGENTS Guide for `testlogs`

## Project purpose
- Spring Boot 4 (Java 21) demo focused on **log validation in integration tests** and trace metadata propagation.
- Primary behavior: `POST /api/examples/process` classifies strings into `SHORTCHAR`, `LONGCHAR`, `NUMERIC`.

## Architecture map (follow this flow first)
- Entry point: `src/main/java/com/tutorial/testlogs/TestlogsApplication.java`.
- HTTP layer: `src/main/java/com/tutorial/testlogs/controller/ExampleController.java` delegates directly to service.
- Domain logic + logging contract: `src/main/java/com/tutorial/testlogs/service/ExampleService.java`.
- Tracing setup: `src/main/java/com/tutorial/testlogs/config/TracingConfiguration.java` builds OpenTelemetry/Micrometer `Tracer`.
- Request-scoped MDC wiring: `src/main/java/com/tutorial/testlogs/config/TracingInterceptor.java` creates span, sets `traceId`/`spanId`, clears on completion.
- MVC registration: `src/main/java/com/tutorial/testlogs/config/WebMvcConfiguration.java` adds the interceptor globally.

## Logging and tracing conventions (project-specific)
- `application.yaml` enforces JSON console logs (`logging.structured.format.console=logstash`).
- Correlation pattern includes MDC keys: `traceId` and `spanId`; tests treat these as contract.
- Service logs are intentional test targets:
  - `INFO` start/completion (`Processing input...`, `Classification complete ...`).
  - `WARN` on empty input.
  - `DEBUG` per-item classification.
- `ExampleService.process(...)` always returns all 3 keys (empty lists when missing category).

## Test strategy and helpers
- Main suite: `src/test/java/com/tutorial/testlogs/controller/ExampleControllerIntegrationTest.java` (`@SpringBootTest` + random port).
- Tests call real HTTP using `java.net.http.HttpClient` (not MockMvc).
- Use `LogCaptor` (`src/test/java/com/tutorial/testlogs/testutil/LogCaptor.java`) with try-with-resources to capture `ExampleService` logs.
- Reuse assertions in `src/test/java/com/tutorial/testlogs/testutil/LogAssertions.java` for:
  - log levels/count/content,
  - MDC regex checks,
  - structured JSON field checks from `CapturedOutput`.
- Property assertions in tests freeze expected values from `src/main/resources/application.yaml`.

## Build and run workflow
- Run tests: `./mvnw test` (or `mvn test` if wrapper is unavailable).
- Run app: `./mvnw spring-boot:run`.
- Package jar: `./mvnw clean package`.
- Dependency/runtime details live in `pom.xml` (Spring Boot 4.0.6, `spring-boot-starter-webmvc-test`, micrometer tracing bridge).

## Editing guardrails for agents
- Preserve log message intent/levels in `ExampleService`; changing text/levels can break integration tests.
- If you alter tracing or logging config, update both behavior tests and property-contract assertions.
- Keep interceptor lifecycle balanced (`span`/`scope` creation in `preHandle`, close/end + MDC cleanup in `afterCompletion`).
- Keep classification key names stable (`SHORTCHAR`, `LONGCHAR`, `NUMERIC`) unless test/API contract is intentionally changed.
- There are no existing repo-level AI instruction files discovered; treat this file as canonical agent guidance.


# Validación de logs en tests con JUnit 5, Spring Boot y Logback (ListAppender)

Este tutorial muestra cómo capturar y validar logs generados por SLF4J/Logback en tests unitarios usando `ListAppender` de Logback. Incluye utilidades helper para reducir boilerplate en tests y ejemplos exhaustivos de aserciones: nivel del log, textos fijos, partes dinámicas, excepciones, MDC y orden de eventos. También ampliamos las secciones teóricas con casos reales de problemas empresariales relacionados con logging y observabilidad.

## Índice
- [Por qué validar logs en tests](#por-que-validar-logs)
- [Problemas reales en producción por mala gestión de logs](#problemas-reales-logs)
- [Propuesta de solución](#propuesta-solucion)
- [Dependencias (pom.xml mínimo)](#dependencias)
- [Patrón básico con ListAppender (recordatorio rápido)](#patron-basico)
- [Utilidades helper para reducir código en tests](#utilidades-helper)
- [Ejemplos de tests usando los helpers](#ejemplos-tests)
- [Aserciones y patterns recomendados (resumen práctico)](#aserciones-patterns)
- [Buenas prácticas](#buenas-practicas)
- [Conclusiones](#conclusiones)

---

<a id="por-que-validar-logs"></a>
## Por qué validar logs en tests

Validar logs en tests aporta garantías sobre la observabilidad y el diagnóstico del sistema. A continuación se listan razones y ejemplos reales de impacto empresarial.

1) Garantizar que los eventos críticos se registran
- Por qué: Las alertas y los playbooks operativos dependen de que ciertos eventos (errores críticos, fallos de integración, degradaciones) sean registrados con nivel y contexto adecuados.
- Caso real típico: Un servicio de pagos cambió un log de ERROR a DEBUG durante una refactorización. Las alertas automáticas dejaron de dispararse y se retrasó la detección de una regresión que causó pérdidas económicas por transacciones fallidas durante horas.

2) Validar presencia de contexto (traceId, requestId, userId)
- Por qué: Sin identificadores correlacionables, los SREs y desarrolladores pierden la capacidad de rastrear incidentes entre servicios.
- Caso real típico: En una plataforma B2B, al eliminarse el `traceId` del log por un cambio en el formato, las investigaciones de incidentes se alargaron días porque las trazas no podían correlacionarse entre microservicios, afectando SLAs de soporte.

3) Evitar fugas de datos sensibles (PII)
- Por qué: Logs que contienen contraseñas, tarjetas o información personal pueden suponer incumplimiento de GDPR o PCI-DSS.
- Caso real típico: Un microservicio registró un payload entero durante un error (incluyendo números parciales de tarjeta). La empresa recibió auditoría y multa por no enmascarar PII en logs.

4) Evitar regresiones silenciosas en mensajes y niveles
- Por qué: Cambios en los mensajes o en el nivel (p.ej. pasar de ERROR a WARN) pueden romper dashboards, alertas y runbooks que dependen de mensajes/textos específicos.
- Caso real típico: Un mensaje de excepción fue editado y dejó de coincidir con la regla de parsing del SIEM; como resultado, alertas críticas no se activaron y se produjo una degradación prolongada.

5) Mejorar la calidad de mensajes (estructura y semántica)
- Por qué: Validar que los logs contienen campos clave permite construir dashboards y métricas derivadas de logs (p. ej. conteo por `errorCode`).
- Caso real típico: Un equipo incluyó `errorCode` en logs y una prueba de contrato garantizó que el campo siempre estaba presente; esto permitió crear alertas por `errorCode` rápidamente.

6) Controlar coste y rendimiento de la observabilidad
- Por qué: Logs excesivos incrementan costes de ingestión, almacenamiento y consulta (p. ej. en Elastic, Datadog, Splunk).
- Caso real típico: After-deploy con `DEBUG` activado en un servicio de alta tasa generó 10x logs, saturando la pipeline de ingestión y ocasionando una factura de observabilidad inesperada en producción.

En resumen: validar logs en tests es parte de la calidad del software y de la higiene operativa. No se trata solo de "siempre loguear", sino de "loguear bien" teniendo en cuenta que entornos cloud tanto la cantidad de logs como su formato son susceptibles de leerse por humanos pero también por servicios.

---

<a id="problemas-reales-logs"></a>
## Problemas reales en producción por mala gestión de logs

Casos públicos conocidos (y su impacto):

- `Equifax (2017)`: durante el incidente de Apache Struts, se reportó que un certificado TLS de inspección estuvo caducado durante meses.
  Impacto: el tráfico no se inspeccionó como se esperaba y se retrasó la detección de actividad maliciosa, ampliando la ventana del ataque.

- `Capital One (2019)`: en el análisis posterior del incidente se destacó la importancia de trazabilidad y monitorización sobre acceso a recursos cloud.
  Impacto: exposición masiva de datos y costes regulatorios/reputacionales muy altos; reforzó la necesidad de auditoría y alertas de seguridad bien configuradas.

- `GitLab (2017)`: aunque el caso fue principalmente de backup/operación, la cronología pública mostró problemas de visibilidad operativa en tiempo real.
  Impacto: pérdida de datos de producción y caída prolongada del servicio; evidenció la necesidad de observabilidad y señales accionables durante incidentes.

- `Incidentes de facturación en plataformas de observabilidad (patrón recurrente)`: varios postmortems públicos describen subidas bruscas de coste por exceso de logs `DEBUG` o alta cardinalidad.
  Impacto: aumento repentino del coste mensual, degradación en búsquedas y necesidad de aplicar recortes urgentes de logging en producción.

Aquí se describen problemas concretos que ocurren en entornos empresariales grandes y cómo suelen originarse.

1) Saturación de pipelines y pérdida de observabilidad
- Origen: Un cambio en una librería o configuración hace que un servicio comience a emitir millones de líneas por segundo (p. ej. logging en bucle sobre cada request).
- Efecto: Flujo de logs (Fluentd/Logstash/Vector) se llena, cola y caduca; dashboards se vuelven lentos o inaccesibles; posibles pérdidas de logs.
- Consecuencia empresarial: Monitoreo inoperable durante la incidencia, incidentes no detectados y fallos en SLA.

2) Costes en la factura de observabilidad
- Origen: Niveles demasiado verbosos en producción o logs sin sampling en servicios de alto QPS.
- Efecto: Incremento en el volumen de datos indexados, consultas más lentas y facturas elevadas por ingestión y egress.
- Caso: Un servicio de comercio electrónico que procesaba fotos por segundo comenzó a incluir un `toString()` muy verboso en cada request — la factura de logging subió sustancialmente hasta detectarlo.

3) Alta cardinalidad de índices y rendimiento degradado
- Origen: Incluir valores de alta cardinalidad (p. ej. userEmail, full UUID) como etiquetas/fields en logs en lugar de en mensaje estructurado.
- Efecto: Índices con tantas etiquetas que las consultas agregadas tardan demasiado o fallan, meters/alerts se vuelven poco fiables.
- Mitigación: normalizar etiquetas, limitar cardinalidad, exportar identificadores como hashes o tags controlados.

4) Fugas de datos y cumplimiento legal
- Origen: Excepciones que serializan objetos con PII, o logs de debugging que vuelcan payloads.
- Efecto: Exposición indebida que puede conllevar sanciones reguladoras y pérdida de confianza del cliente.
- Consecuencia: Auditorías, multas y costes de remediación.

5) Dependencia frágil en formatos de mensaje
- Origen: Scripts y reglas (regex) en el SIEM que dependen de mensajes exactos; un cambio en la cadena rompe detección automática.
- Efecto: Alertas que dejan de dispararse en el peor momento.
- Mitigación: Estandarizar mensajes, preferir logs estructurados (JSON) y asegurar contract tests.

---

<a id="propuesta-solucion"></a>
## Propuesta de solución

La solución de este proyecto combina validación funcional y validación de observabilidad en una misma suite de tests:

- Pruebas de integración del endpoint para validar respuesta HTTP y payload JSON.
- Captura de logs del servicio con `LogCaptor` para verificar nivel, contenido y cantidad de eventos.
- Aserciones reutilizables con `LogAssertions` para reducir boilerplate y estandarizar checks.
- Validación de contexto de trazas (`traceId`, `spanId`) en MDC para asegurar correlación entre logs.
- Validación de salida estructurada en consola (`CapturedOutput`) para confirmar campos clave de logging.

Con este enfoque se detectan regresiones funcionales y de observabilidad antes de llegar a producción.

---

<a id="dependencias"></a>
## Dependencias (pom.xml mínimo)

```xml
<parent>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-parent</artifactId>
  <version>4.0.6</version>
</parent>

<properties>
  <java.version>21</java.version>
</properties>

<dependencies>
  <dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-webmvc</artifactId>
  </dependency>
  <dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
  </dependency>
  <dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-tracing-bridge-otel</artifactId>
  </dependency>
  <dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-webmvc-test</artifactId>
    <scope>test</scope>
  </dependency>
</dependencies>
```

---

<a id="patron-basico"></a>
## Patrón básico con ListAppender (recordatorio rápido)

- Obtener logger: `Logger logger = (Logger) LoggerFactory.getLogger(Clase.class);`
- Crear listAppender: `ListAppender<ILoggingEvent> appender = new ListAppender<>();`
- `appender.start(); logger.addAppender(appender);`
- Ejecutar la lógica.
- Leer `appender.list` (List<ILoggingEvent>).
- Cleanup: `logger.detachAppender(appender); appender.stop();`

Este patrón funciona pero se repite en muchos tests; por eso añadimos helpers.

---

<a id="utilidades-helper"></a>
## Utilidades helper para reducir código en tests

Presentamos `LogCaptor`, una pequeña utilidad que envuelve `ListAppender` y simplifica su uso con `try-with-resources`. También incluimos `LogAssertions` con aserciones frecuentes.

Archivo: LogCaptor.java

```java name=LogCaptor.java
public final class LogCaptor implements AutoCloseable {
    private final Logger logger;
    private final ListAppender<ILoggingEvent> appender;

    private LogCaptor(Class<?> target) {
        this.logger = (Logger) LoggerFactory.getLogger(target);
        this.appender = new ListAppender<>();
        this.appender.start();
        this.logger.addAppender(this.appender);
    }

    public static LogCaptor forClass(Class<?> target) {
        return new LogCaptor(target);
    }

    // ...otros métodos helper...

    public List<ILoggingEvent> getEvents() {
        return List.copyOf(appender.list);
    }

    public List<String> getMessages(Level level) {
        return getEvents(level).stream()
                .map(ILoggingEvent::getFormattedMessage)
                .toList();
    }

    public List<ILoggingEvent> getEvents(Level level) {
        return getEvents().stream()
                .filter(event -> event.getLevel().equals(level))
                .toList();
    }

    public List<String> getMdcValues(Level level, String key) {
        return getEvents(level).stream()
                .flatMap(event -> getMdcValue(event, key))
                .toList();
    }

    private Stream<String> getMdcValue(ILoggingEvent event, String key) {
        String value = event.getMDCPropertyMap().get(key);
        if (value == null || value.isBlank()) {
            return Stream.empty();
        }
        return Stream.of(value);
    }

    public long count(Level level) {
        return getEvents(level).size();
    }

    public int size() {
        return getEvents().size();
    }

    @Override
    public void close() {
        logger.detachAppender(appender);
        appender.stop();
    }
}
```

Archivo: LogAssertions.java

```java name=LogAssertions.java
public final class LogAssertions {

    private LogAssertions() {
    }

    public static void assertProperty(Environment environment, String key, String expectedValue) {
        assertThat(environment.getProperty(key))
                .as("Property '%s'", key)
                .isEqualTo(expectedValue);
    }

    public static void assertContains(LogCaptor captor, Level level, String substring) {
        assertThat(containsMessage(captor, level, substring))
                .as("Expected a %s log containing '%s'", level, substring)
                .isTrue();
    }

    // ...otros métodos de aserción...

    public static boolean containsMessage(LogCaptor captor, Level level, String substring) {
        return captor.getMessages(level).stream().anyMatch(message -> message.contains(substring));
    }

    public static void assertLevelCount(LogCaptor captor, Level level, int expectedCount) {
        assertThat(captor.count(level))
                .as("Log count for level %s", level)
                .isEqualTo(expectedCount);
    }

    public static void assertAllMessagesContain(LogCaptor captor, Level level, String substring) {
        assertThat(captor.getMessages(level))
                .as("All %s messages should contain '%s'", level, substring)
                .isNotEmpty()
                .allMatch(message -> message.contains(substring));
    }

    public static void assertNoLogsAtLevel(LogCaptor captor, Level level) {
        assertThat(captor.getEvents(level))
                .as("Expected no logs at level %s", level)
                .isEmpty();
    }

    public static void assertStructuredField(String output, String field, String expectedValue) {
        assertThat(output)
                .contains("\"" + field + "\":\"" + expectedValue + "\"");
    }

    public static void assertStructuredFieldExists(String output, String field) {
        assertThat(output)
                .contains("\"" + field + "\"");
    }

    public static void assertStructuredFields(String output, Map<String, String> expectedFields) {
        expectedFields.forEach((field, value) -> assertStructuredField(output, field, value));
    }

    public static void assertStructuredMessageContains(String output, String fragment) {
        assertThat(output)
                .contains("\"message\":")
                .contains(fragment);
    }

    public static void assertMdcFieldMatches(LogCaptor captor, Level level, String key, String regex) {
        Pattern pattern = Pattern.compile(regex);
        List<String> values = captor.getMdcValues(level, key);

        assertThat(values)
                .as("MDC key '%s' should exist in level %s logs", key, level)
                .isNotEmpty()
                .allMatch(value -> pattern.matcher(value).matches());
    }

    public static void assertStructuredFieldMatches(String output, String field, String regex) {
        Pattern pattern = Pattern.compile("\\\"" + Pattern.quote(field) + "\\\":\\\"(" + regex + ")\\\"");
        assertThat(pattern.matcher(output).find())
                .as("Structured field '%s' should match regex '%s'", field, regex)
                .isTrue();
    }
}
```

Estas utilidades simplifican el patrón repetitivo de attach/detach y ofrecen funciones frecuentes como `assertContains`, `assertLevelCount`, `assertMdcFieldMatches` y aserciones sobre logs estructurados.

---

<a id="ejemplos-tests"></a>
## Ejemplos de tests usando los helpers

Ejemplo 1 — clasificación y logs INFO vía controlador:

```java name=ExampleControllerIntegrationTest.java
@Test
void processClassifiesCorrectlyThroughController() throws Exception {
    List<String> input = List.of("hi", "hello", "thisisalongstring", "42", "3.14", "world");

    try (LogCaptor lc = LogCaptor.forClass(ExampleService.class)) {
        HttpResponse<String> response = postProcess(input);
        assertThat(response.statusCode()).isEqualTo(200);

        Map<String, List<String>> result = objectMapper.readValue(response.body(), new TypeReference<>() {});

        assertThat(result.get("SHORTCHAR")).containsExactlyInAnyOrder("hi", "hello", "world");
        assertThat(result.get("LONGCHAR")).containsExactlyInAnyOrder("thisisalongstring");
        assertThat(result.get("NUMERIC")).containsExactlyInAnyOrder("42", "3.14");

        assertContains(lc, Level.INFO, "Processing input with 6 items");
        assertContains(lc, Level.INFO, "Classification complete");
        assertLevelCount(lc, Level.INFO, 2);
    }
}
```

Ejemplo 2 — entrada vacía y log WARN:

```java name=ExampleControllerIntegrationTest.java
@Test
void processEmptyInputReturnsEmptyListsAndWarnLog() throws Exception {
    try (LogCaptor lc = LogCaptor.forClass(ExampleService.class)) {
        HttpResponse<String> response = postProcess(List.of());
        assertThat(response.statusCode()).isEqualTo(200);

        Map<String, List<String>> result = objectMapper.readValue(response.body(), new TypeReference<>() {});

        assertThat(result.get("SHORTCHAR")).isEmpty();
        assertThat(result.get("LONGCHAR")).isEmpty();
        assertThat(result.get("NUMERIC")).isEmpty();

        assertContains(lc, Level.WARN, "Received empty input");
        assertLevelCount(lc, Level.WARN, 1);
    }
}
```

Ejemplo 3 — validación de MDC:

```java name=ExampleControllerIntegrationTest.java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ExampleControllerIntegrationTest {

    private static final String TRACE_ID_REGEX = "[a-f0-9]{32}";
    private static final String SPAN_ID_REGEX = "[a-f0-9]{16}";

    @Autowired
    private ObjectMapper objectMapper;

    @Value("${local.server.port}")
    private int port;

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @Test
    void serviceLogsShouldIncludeTraceIdentifiersInMdc() throws Exception {
        try (LogCaptor lc = LogCaptor.forClass(ExampleService.class)) {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:" + port + "/api/examples/process"))
                    .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(List.of("a", "1"))))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            assertThat(response.statusCode()).isEqualTo(200);

            assertMdcFieldMatches(lc, Level.INFO, "traceId", TRACE_ID_REGEX);
            assertMdcFieldMatches(lc, Level.INFO, "spanId", SPAN_ID_REGEX);
        }
    }
}
```

Ejemplo 4 — validación de logs estructurados en consola:

```java name=ExampleControllerIntegrationTest.java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ExtendWith(OutputCaptureExtension.class)
class ExampleControllerIntegrationTest {

    @Autowired
    private ObjectMapper objectMapper;

    @Value("${local.server.port}")
    private int port;

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @Test
    void consoleLogsShouldContainStructuredFields(CapturedOutput output) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/examples/process"))
                .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(List.of("alpha", "123"))))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);

        String logs = output.getOut();
        assertStructuredFieldExists(logs, "@timestamp");
        assertStructuredField(logs, "service.name", "testlogs");
        assertStructuredField(logs, "level", "INFO");
        assertStructuredField(logs, "level", "DEBUG");
    }
}
```

Estos ejemplos muestran tests mucho más concisos y menos propensos a olvidar `detach` o `stop`.

---

<a id="aserciones-patterns"></a>
## Aserciones y patterns recomendados (resumen práctico)

- `Código HTTP`: valida `response.statusCode()` antes de cualquier aserción de logs.
- `Resultado funcional`: comprueba la respuesta JSON (clasificación) además del log; evita tests acoplados solo al texto.
- `Nivel y contenido`: usa `assertContains` + `assertLevelCount` para asegurar semántica y volumen esperado.
- `MDC`: valida `traceId` y `spanId` con regex (`assertMdcFieldMatches`) en niveles concretos.
- `Logs estructurados`: usa `assertStructuredFieldExists`, `assertStructuredField` y `assertStructuredFieldMatches` sobre `CapturedOutput`.
- `Configuración`: añade aserciones de propiedades (`assertProperty`) para congelar contratos de logging/tracing.

---

<a id="buenas-practicas"></a>
## Buenas prácticas

- Centraliza helpers (`LogCaptor`, `LogAssertions`) para reducir boilerplate y mantener consistencia entre pruebas.
- Prioriza pruebas de integración HTTP para validar observabilidad real (MDC + salida estructurada), no solo logs internos.
- Mantén aserciones estables: busca fragmentos relevantes y regex de formato, evitando acoplarte a mensajes completos.
- Verifica siempre camino feliz y edge cases (entrada vacía, solo numéricos, mezcla de categorías).
- Incluye pruebas de contrato de configuración (`application.yaml`) para detectar regresiones tempranas.
- Evita duplicar utilidades de captura de logs; reutiliza una sola API de assertions en todo el proyecto.

---

<a id="conclusiones"></a>
## Conclusiones

Validar logs en tests no es un extra, es parte del contrato de calidad y observabilidad del servicio. En este proyecto, la combinación de `LogCaptor` + `LogAssertions` + pruebas de integración en `ExampleControllerIntegrationTest` permite comprobar de forma fiable:

- comportamiento funcional del endpoint,
- calidad y nivel de los logs,
- propagación de `traceId`/`spanId`,
- y formato estructurado de salida.

Con este enfoque, las regresiones de observabilidad se detectan igual que cualquier regresión funcional antes de llegar a producción.

---


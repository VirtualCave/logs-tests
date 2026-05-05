package com.tutorial.testlogs.controller;

import ch.qos.logback.classic.Level;
import com.tutorial.testlogs.service.ExampleService;
import com.tutorial.testlogs.testutil.LogCaptor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.core.env.Environment;
import org.springframework.http.MediaType;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;

import static com.tutorial.testlogs.testutil.LogAssertions.*;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ExtendWith(OutputCaptureExtension.class)
class ExampleControllerIntegrationTest {

    private static final String TRACE_ID_REGEX = "[a-f0-9]{32}";
    private static final String SPAN_ID_REGEX = "[a-f0-9]{16}";

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private Environment environment;

    @Value("${local.server.port}")
    private int port;

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @Test
    void processClassifiesCorrectlyThroughController() throws Exception {
        List<String> input = List.of("hi", "hello", "thisisalongstring", "42", "3.14", "world");

        try (LogCaptor lc = LogCaptor.forClass(ExampleService.class)) {
            HttpResponse<String> response = postProcess(input);
            assertThat(response.statusCode()).isEqualTo(200);

            String json = response.body();

            Map<String, List<String>> result = objectMapper.readValue(json, new TypeReference<>() {
            });

            assertThat(result.get("SHORTCHAR")).containsExactlyInAnyOrder("hi", "hello", "world");
            assertThat(result.get("LONGCHAR")).containsExactlyInAnyOrder("thisisalongstring");
            assertThat(result.get("NUMERIC")).containsExactlyInAnyOrder("42", "3.14");

            assertContains(lc, Level.INFO, "Processing input with 6 items");
            assertContains(lc, Level.INFO, "Classification complete");
            assertLevelCount(lc, Level.INFO, 2);
            assertMdcFieldMatches(lc, Level.INFO, "traceId", TRACE_ID_REGEX);
            assertMdcFieldMatches(lc, Level.INFO, "spanId", SPAN_ID_REGEX);
        }
    }

    @Test
    void processEmptyInputReturnsEmptyListsAndWarnLog() throws Exception {
        try (LogCaptor lc = LogCaptor.forClass(ExampleService.class)) {
            HttpResponse<String> response = postProcess(List.of());
            assertThat(response.statusCode()).isEqualTo(200);

            String json = response.body();

            Map<String, List<String>> result = objectMapper.readValue(json, new TypeReference<>() {
            });

            assertThat(result.get("SHORTCHAR")).isEmpty();
            assertThat(result.get("LONGCHAR")).isEmpty();
            assertThat(result.get("NUMERIC")).isEmpty();

            assertContains(lc, Level.WARN, "Received empty input");
            assertLevelCount(lc, Level.WARN, 1);
            assertMdcFieldMatches(lc, Level.WARN, "traceId", TRACE_ID_REGEX);
            assertMdcFieldMatches(lc, Level.WARN, "spanId", SPAN_ID_REGEX);
        }
    }

    @Test
    void processOnlyNumericsKeepsDebugClassificationLogs() throws Exception {
        List<String> input = List.of("100", "200", "-5", "0.99");

        try (LogCaptor lc = LogCaptor.forClass(ExampleService.class)) {
            HttpResponse<String> response = postProcess(input);
            assertThat(response.statusCode()).isEqualTo(200);

            String json = response.body();

            Map<String, List<String>> result = objectMapper.readValue(json, new TypeReference<>() {
            });

            assertThat(result.get("NUMERIC")).hasSize(4);
            assertThat(result.get("SHORTCHAR")).isEmpty();
            assertThat(result.get("LONGCHAR")).isEmpty();

            assertLevelCount(lc, Level.DEBUG, 4);
            assertAllMessagesContain(lc, Level.DEBUG, "NUMERIC");
            assertNoLogsAtLevel(lc, Level.WARN);
        }
    }

    @Test
    void loggingConfigurationShouldMatchCloudDefaults() {
        assertProperty(environment, "spring.application.name", "testlogs");
        assertProperty(environment, "logging.level.root", "INFO");
        assertProperty(environment, "logging.level.com.tutorial.testlogs", "DEBUG");
        assertProperty(environment, "logging.level.org.springframework", "INFO");
        assertProperty(environment, "logging.include-application-name", "false");
        assertProperty(environment, "logging.exception-conversion-word", "%wEx");
        assertProperty(environment, "logging.pattern.correlation", "[testlogs,traceId=%X{traceId:-},spanId=%X{spanId:-}] ");
        assertProperty(environment, "logging.structured.format.console", "logstash");
        assertProperty(environment, "logging.structured.json.add.service.name", "testlogs");
        assertProperty(environment, "logging.structured.json.add.service.version", "unknown");
        assertProperty(environment, "logging.structured.json.add.service.environment", "local");
        assertProperty(environment, "logging.structured.json.add.cloud.region", "unknown");
        assertProperty(environment, "management.tracing.sampling.probability", "1.0");
    }

    @Test
    void consoleLogsShouldContainStructuredTracingMetadata(CapturedOutput output) throws Exception {
        HttpResponse<String> response = postProcess(List.of("alpha", "123", "thisisalongstring"));
        assertThat(response.statusCode()).isEqualTo(200);

        String logs = output.getOut();

        assertStructuredFieldExists(logs, "@timestamp");
        assertStructuredFields(logs, Map.of(
                "@version", "1",
                "logger_name", "com.tutorial.testlogs.service.ExampleService",
                "service.name", "testlogs",
                "service.version", "unknown",
                "service.environment", "local",
                "cloud.region", "unknown"
        ));
        assertStructuredField(logs, "level", "INFO");
        assertStructuredField(logs, "level", "DEBUG");
        assertStructuredFieldMatches(logs, "traceId", TRACE_ID_REGEX);
        assertStructuredFieldMatches(logs, "spanId", SPAN_ID_REGEX);
        assertStructuredMessageContains(logs, "Processing input with 3 items");
        assertStructuredMessageContains(logs, "Classification complete");
        assertStructuredMessageContains(logs, "SHORTCHAR=1, LONGCHAR=1, NUMERIC=1");
    }

    private HttpResponse<String> postProcess(List<String> input) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/examples/process"))
                .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(input)))
                .build();

        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }
}


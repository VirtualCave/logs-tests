package com.tutorial.testlogs.testutil;

import ch.qos.logback.classic.Level;
import org.springframework.core.env.Environment;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

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
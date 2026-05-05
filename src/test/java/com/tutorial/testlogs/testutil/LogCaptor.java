package com.tutorial.testlogs.testutil;


import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.stream.Stream;

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

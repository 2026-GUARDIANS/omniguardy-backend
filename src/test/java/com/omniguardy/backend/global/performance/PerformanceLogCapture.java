package com.omniguardy.backend.global.performance;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import java.util.List;

public final class PerformanceLogCapture implements AutoCloseable {
    private final Logger logger = (Logger) LoggerFactory.getLogger("omniguardy.performance");
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
    public PerformanceLogCapture() { appender.start(); logger.addAppender(appender); }
    public List<JsonNode> events() {
        var mapper = JsonMapper.builder().build();
        return appender.list.stream().map(event -> mapper.readTree(event.getFormattedMessage())).toList();
    }
    public JsonNode stage(String stage) {
        return events().stream().filter(event -> stage.equals(event.path("stage").asText())).findFirst().orElseThrow();
    }
    @Override public void close() { logger.detachAppender(appender); appender.stop(); }
}

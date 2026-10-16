package com.omniguardy.backend.domain.securityevent.application.metrics;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.omniguardy.backend.domain.securityevent.domain.model.EventTriggerType;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentPerformanceMetricsTest {

    @Test
    void recordsAggregatesAndLogsSuccessFailureAndTimeout() {
        AgentPerformanceMetrics metrics = new AgentPerformanceMetrics();
        Logger logger = (Logger) LoggerFactory.getLogger(AgentPerformanceMetrics.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);

        try {
            metrics.recordLlmCall(EventTriggerType.AUDIO, TimeUnit.MILLISECONDS.toNanos(5), true, null);
            metrics.recordAgentExecution(
                    EventTriggerType.AUDIO, TimeUnit.MILLISECONDS.toNanos(10), true, null);
            metrics.recordAgentExecution(EventTriggerType.KEYPAD, TimeUnit.MILLISECONDS.toNanos(30), false,
                    new dev.langchain4j.exception.TimeoutException("timed out"));

            AgentPerformanceMetrics.Snapshot snapshot = metrics.snapshot();
            assertEquals(2, snapshot.agentExecutionCount());
            assertEquals(20.0, snapshot.agentAverageMs());
            assertEquals(30.0, snapshot.agentP95Ms());
            assertEquals(10.0, snapshot.eventTypeAverageMs().get(EventTriggerType.AUDIO));
            assertEquals(30.0, snapshot.eventTypeAverageMs().get(EventTriggerType.KEYPAD));
            assertEquals(1, snapshot.llmCallCount());
            assertEquals(1, snapshot.agentFailureCount());
            assertEquals(1, snapshot.agentTimeoutCount());

            assertTrue(appender.list.stream().map(ILoggingEvent::getFormattedMessage)
                    .anyMatch(message -> message.contains(
                            "agent_execution eventType=AUDIO durationMs=10.0 success=true timeout=false")));
            assertTrue(appender.list.stream().map(ILoggingEvent::getFormattedMessage)
                    .anyMatch(message -> message.contains(
                            "agent_execution eventType=KEYPAD durationMs=30.0 success=false timeout=true")));
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }
}

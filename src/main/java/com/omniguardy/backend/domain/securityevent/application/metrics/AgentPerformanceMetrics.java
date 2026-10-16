package com.omniguardy.backend.domain.securityevent.application.metrics;

import com.omniguardy.backend.domain.securityevent.domain.model.EventTriggerType;
import dev.langchain4j.exception.TimeoutException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAdder;

@Slf4j
@Component
public class AgentPerformanceMetrics {
    private static final int P95_SAMPLE_SIZE = 1_024;

    private final LongAdder agentExecutionCount = new LongAdder();
    private final LongAdder agentTotalNanos = new LongAdder();
    private final LongAdder llmCallCount = new LongAdder();
    private final LongAdder agentFailureCount = new LongAdder();
    private final LongAdder agentTimeoutCount = new LongAdder();
    private final Map<EventTriggerType, EventMetrics> eventMetrics = new EnumMap<>(EventTriggerType.class);
    private final ArrayDeque<Long> recentAgentDurationsNanos = new ArrayDeque<>(P95_SAMPLE_SIZE);

    public AgentPerformanceMetrics() {
        for (EventTriggerType eventType : EventTriggerType.values()) {
            eventMetrics.put(eventType, new EventMetrics());
        }
    }

    public void recordAgentExecution(EventTriggerType eventType, long durationNanos,
                                     boolean success, Throwable failure) {
        agentExecutionCount.increment();
        agentTotalNanos.add(durationNanos);
        if (!success) agentFailureCount.increment();
        boolean timeout = !success && isTimeout(failure);
        if (timeout) agentTimeoutCount.increment();

        if (eventType != null) {
            eventMetrics.get(eventType).record(durationNanos);
        }
        synchronized (recentAgentDurationsNanos) {
            if (recentAgentDurationsNanos.size() == P95_SAMPLE_SIZE) {
                recentAgentDurationsNanos.removeFirst();
            }
            recentAgentDurationsNanos.addLast(durationNanos);
        }

        log.info("agent_execution eventType={} durationMs={} success={} timeout={}",
                eventType == null ? "UNKNOWN" : eventType.name(), millis(durationNanos), success, timeout);

        Snapshot snapshot = snapshot();
        log.info("agent_metrics executions={} averageMs={} p95Ms={} p95SampleCount={} "
                        + "eventTypeAverageMs={} llmCallCount={} failureCount={} timeoutCount={}",
                snapshot.agentExecutionCount(), snapshot.agentAverageMs(), snapshot.agentP95Ms(),
                snapshot.p95SampleCount(), snapshot.eventTypeAverageMs(), snapshot.llmCallCount(),
                snapshot.agentFailureCount(), snapshot.agentTimeoutCount());
    }

    public void recordLlmCall(EventTriggerType eventType, long durationNanos,
                              boolean success, Throwable failure) {
        llmCallCount.increment();
        log.info("llm_api_call eventType={} durationMs={} success={} timeout={}",
                eventType == null ? "UNKNOWN" : eventType.name(), millis(durationNanos), success,
                !success && isTimeout(failure));
    }

    public Snapshot snapshot() {
        long executions = agentExecutionCount.sum();
        Map<EventTriggerType, Double> averages = new EnumMap<>(EventTriggerType.class);
        eventMetrics.forEach((eventType, metrics) -> averages.put(eventType, metrics.averageMs()));

        long[] durations;
        synchronized (recentAgentDurationsNanos) {
            durations = recentAgentDurationsNanos.stream().mapToLong(Long::longValue).toArray();
        }
        Arrays.sort(durations);
        double p95Ms = durations.length == 0 ? 0.0
                : millis(durations[(int) Math.ceil(durations.length * 0.95) - 1]);

        return new Snapshot(executions, averageMs(agentTotalNanos.sum(), executions), p95Ms,
                durations.length, Collections.unmodifiableMap(averages), llmCallCount.sum(),
                agentFailureCount.sum(), agentTimeoutCount.sum());
    }

    private boolean isTimeout(Throwable failure) {
        Throwable current = failure;
        while (current != null) {
            if (current instanceof TimeoutException
                    || current instanceof java.util.concurrent.TimeoutException
                    || current instanceof SocketTimeoutException
                    || current instanceof HttpTimeoutException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private static double averageMs(long totalNanos, long count) {
        return count == 0 ? 0.0 : millis(totalNanos / count);
    }

    private static double millis(long nanos) {
        double value = nanos / (double) TimeUnit.MILLISECONDS.toNanos(1);
        return Math.round(value * 1_000.0) / 1_000.0;
    }

    private static final class EventMetrics {
        private final LongAdder count = new LongAdder();
        private final LongAdder totalNanos = new LongAdder();

        private void record(long durationNanos) {
            count.increment();
            totalNanos.add(durationNanos);
        }

        private double averageMs() {
            return AgentPerformanceMetrics.averageMs(totalNanos.sum(), count.sum());
        }
    }

    public record Snapshot(long agentExecutionCount, double agentAverageMs, double agentP95Ms,
                           int p95SampleCount, Map<EventTriggerType, Double> eventTypeAverageMs,
                           long llmCallCount, long agentFailureCount, long agentTimeoutCount) {
    }
}

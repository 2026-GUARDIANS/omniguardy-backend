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
    private static final int DEFAULT_SAMPLE_SIZE = 1_024;

    private final LongAdder agentExecutionCount = new LongAdder();
    private final LongAdder llmCallCount = new LongAdder();
    private final LongAdder agentFailureCount = new LongAdder();
    private final LongAdder agentTimeoutCount = new LongAdder();
    private final int sampleSize;
    private final ArrayDeque<ExecutionSample> recentAgentExecutions;

    public AgentPerformanceMetrics() {
        this(DEFAULT_SAMPLE_SIZE);
    }

    AgentPerformanceMetrics(int sampleSize) {
        if (sampleSize <= 0) throw new IllegalArgumentException("sampleSize must be positive");
        this.sampleSize = sampleSize;
        this.recentAgentExecutions = new ArrayDeque<>(sampleSize);
    }

    public void recordAgentExecution(EventTriggerType eventType, long durationNanos,
                                     boolean success, Throwable failure) {
        agentExecutionCount.increment();
        if (!success) agentFailureCount.increment();
        boolean timeout = !success && isTimeout(failure);
        if (timeout) agentTimeoutCount.increment();

        synchronized (recentAgentExecutions) {
            if (recentAgentExecutions.size() == sampleSize) {
                recentAgentExecutions.removeFirst();
            }
            recentAgentExecutions.addLast(new ExecutionSample(eventType, durationNanos));
        }

        log.info("agent_execution eventType={} durationMs={} success={} timeout={}",
                eventType == null ? "UNKNOWN" : eventType.name(), millis(durationNanos), success, timeout);

        Snapshot snapshot = snapshot();
        log.info("agent_metrics executions={} windowAverageMs={} windowP95Ms={} windowSampleCount={} "
                        + "eventTypeWindowAverageMs={} llmCallCount={} failureCount={} timeoutCount={}",
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
        ExecutionSample[] samples;
        synchronized (recentAgentExecutions) {
            samples = recentAgentExecutions.toArray(ExecutionSample[]::new);
        }

        long[] durations = Arrays.stream(samples).mapToLong(ExecutionSample::durationNanos).toArray();
        long totalNanos = Arrays.stream(durations).sum();
        Arrays.sort(durations);
        double p95Ms = durations.length == 0 ? 0.0
                : millis(durations[(int) Math.ceil(durations.length * 0.95) - 1]);

        Map<EventTriggerType, Double> averages = eventTypeAverages(samples);
        return new Snapshot(executions, averageMs(totalNanos, samples.length), p95Ms,
                samples.length, Collections.unmodifiableMap(averages), llmCallCount.sum(),
                agentFailureCount.sum(), agentTimeoutCount.sum());
    }

    private Map<EventTriggerType, Double> eventTypeAverages(ExecutionSample[] samples) {
        Map<EventTriggerType, Long> totals = new EnumMap<>(EventTriggerType.class);
        Map<EventTriggerType, Long> counts = new EnumMap<>(EventTriggerType.class);
        for (EventTriggerType eventType : EventTriggerType.values()) {
            totals.put(eventType, 0L);
            counts.put(eventType, 0L);
        }
        for (ExecutionSample sample : samples) {
            if (sample.eventType() != null) {
                totals.merge(sample.eventType(), sample.durationNanos(), Long::sum);
                counts.merge(sample.eventType(), 1L, Long::sum);
            }
        }

        Map<EventTriggerType, Double> averages = new EnumMap<>(EventTriggerType.class);
        for (EventTriggerType eventType : EventTriggerType.values()) {
            averages.put(eventType, averageMs(totals.get(eventType), counts.get(eventType)));
        }
        return averages;
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

    private record ExecutionSample(EventTriggerType eventType, long durationNanos) {
    }

    public record Snapshot(long agentExecutionCount, double agentAverageMs, double agentP95Ms,
                           int p95SampleCount, Map<EventTriggerType, Double> eventTypeAverageMs,
                           long llmCallCount, long agentFailureCount, long agentTimeoutCount) {
    }
}

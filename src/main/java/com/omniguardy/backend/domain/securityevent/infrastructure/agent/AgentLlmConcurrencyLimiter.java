package com.omniguardy.backend.domain.securityevent.infrastructure.agent;

import com.omniguardy.backend.global.performance.PerformanceLog;

import java.util.concurrent.Semaphore;
import java.util.function.Supplier;

/** 모든 Agent 진입점에서 외부 LLM 동시 호출 상한을 공유한다. */
public final class AgentLlmConcurrencyLimiter {
    private final Semaphore permits;

    public AgentLlmConcurrencyLimiter(int maxConcurrentCalls) {
        if (maxConcurrentCalls < 1) throw new IllegalArgumentException("maxConcurrentCalls must be positive");
        permits = new Semaphore(maxConcurrentCalls, true);
    }

    public <T> T call(Supplier<T> call) {
        long queuedAt = System.nanoTime();
        try {
            permits.acquire();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for Agent LLM permit", interrupted);
        }
        PerformanceLog.duration("AGENT_LLM_WAIT", System.nanoTime() - queuedAt);
        try { return call.get(); }
        finally { permits.release(); }
    }
}

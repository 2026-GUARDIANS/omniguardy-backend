package com.omniguardy.backend.domain.securityevent.infrastructure.agent;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentLlmConcurrencyLimiterTest {
    @Test void neverExceedsConfiguredConcurrentCalls() throws Exception {
        var limiter = new AgentLlmConcurrencyLimiter(2);
        var entered = new CountDownLatch(2);
        var release = new CountDownLatch(1);
        var active = new AtomicInteger();
        var maximum = new AtomicInteger();
        var pool = Executors.newFixedThreadPool(3);
        try {
            var calls = java.util.stream.IntStream.range(0, 3)
                    .mapToObj(index -> pool.submit(() -> limiter.call(() -> {
                        int current = active.incrementAndGet();
                        maximum.accumulateAndGet(current, Math::max);
                        entered.countDown();
                        try { assertTrue(release.await(10, TimeUnit.SECONDS)); }
                        catch (InterruptedException interrupted) {
                            Thread.currentThread().interrupt();
                            throw new AssertionError(interrupted);
                        } finally { active.decrementAndGet(); }
                        return index;
                    }))).toList();
            assertTrue(entered.await(10, TimeUnit.SECONDS));
            assertEquals(2, maximum.get());
            release.countDown();
            for (var call : calls) call.get(10, TimeUnit.SECONDS);
            assertEquals(2, maximum.get());
        } finally {
            release.countDown();
            pool.shutdownNow();
        }
    }
}

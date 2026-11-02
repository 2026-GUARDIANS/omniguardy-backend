package com.omniguardy.backend.domain.visionsession.infrastructure.agent;

import com.omniguardy.backend.domain.visionsession.application.model.StreamingAgentPolicy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import java.time.Duration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration
public class StreamingAgentConfiguration {
    // Keep slow Agent calls off the scheduler used by the existing V8 Vision poller.
    @Bean(name = "taskScheduler") ThreadPoolTaskScheduler visionTaskScheduler() {
        return scheduler("vision-poll-");
    }
    @Bean(name = "streamingAgentTaskScheduler") ThreadPoolTaskScheduler agentTaskScheduler() {
        return scheduler("streaming-agent-");
    }
    private ThreadPoolTaskScheduler scheduler(String prefix) {
        var scheduler = new ThreadPoolTaskScheduler(); scheduler.setPoolSize(1); scheduler.setThreadNamePrefix(prefix);
        return scheduler;
    }
    @Bean StreamingAgentPolicy streamingAgentPolicy(
            @Value("${omniguardy.streaming-agent.preparation-lease:30s}") Duration preparation,
            @Value("${omniguardy.streaming-agent.calling-lease:5m}") Duration calling,
            @Value("${omniguardy.streaming-agent.retry-delay:10s}") Duration retry,
            @Value("${omniguardy.streaming-agent.max-attempts:3}") int attempts,
            @Value("${omniguardy.streaming-agent.batch-size:10}") int batch) {
        return new StreamingAgentPolicy(preparation, calling, retry, attempts, batch);
    }
}

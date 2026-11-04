package com.omniguardy.backend.domain.visionsession.infrastructure.agent;

import com.omniguardy.backend.domain.visionsession.application.model.StreamingAgentPolicy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import java.time.Duration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import java.util.concurrent.ThreadPoolExecutor;

@Configuration
public class StreamingAgentConfiguration {
    // Keep slow Agent calls off the scheduler used by the existing V8 Vision poller.
    @Bean(name = "taskScheduler") ThreadPoolTaskScheduler visionTaskScheduler() {
        return scheduler("vision-poll-", 1);
    }
    @Bean(name = "streamingAgentTaskScheduler") ThreadPoolTaskScheduler agentTaskScheduler(
            @Value("${omniguardy.streaming-agent.scheduler-pool-size:1}") int poolSize) {
        return scheduler("streaming-agent-scheduler-", poolSize);
    }
    @Bean(name = "streamingAgentWorkerExecutor") ThreadPoolTaskExecutor agentWorkerExecutor(
            @Value("${omniguardy.streaming-agent.worker-pool-size:4}") int poolSize,
            @Value("${omniguardy.streaming-agent.worker-queue-capacity:20}") int queueCapacity) {
        var executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(poolSize);
        executor.setMaxPoolSize(poolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix("streaming-agent-worker-");
        // 포화 시 스케줄러 스레드가 직접 실행해 작업 유실 없이 자연스럽게 역압을 건다.
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(10);
        return executor;
    }
    private ThreadPoolTaskScheduler scheduler(String prefix, int poolSize) {
        var scheduler = new ThreadPoolTaskScheduler(); scheduler.setPoolSize(poolSize); scheduler.setThreadNamePrefix(prefix);
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

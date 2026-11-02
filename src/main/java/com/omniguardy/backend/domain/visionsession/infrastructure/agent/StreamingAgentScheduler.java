package com.omniguardy.backend.domain.visionsession.infrastructure.agent;

import com.omniguardy.backend.domain.visionsession.application.usecase.ProcessStreamingAgentService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = {"omniguardy.streaming-agent.enabled", "ai.agent.enabled"}, havingValue = "true")
public class StreamingAgentScheduler {
    private final ProcessStreamingAgentService worker;
    @Scheduled(fixedDelayString = "${omniguardy.streaming-agent.scheduler-delay-ms:1000}", scheduler = "streamingAgentTaskScheduler")
    public void process() { worker.processDue(); }
}

package com.omniguardy.backend.domain.securityevent.infrastructure.agent;

import com.omniguardy.backend.domain.securityevent.application.metrics.AgentPerformanceMetrics;
import com.omniguardy.backend.domain.securityevent.application.port.out.RiskAssessmentPort;
import com.omniguardy.backend.domain.securityevent.domain.error.SecurityEventErrorCode;
import com.omniguardy.backend.global.error.exception.BusinessException;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.service.AiServices;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;

@Configuration
public class OpenAiAgentConfig {
    @Bean
    @ConditionalOnProperty(name = "ai.agent.enabled", havingValue = "true")
    ChatModel agentChatModel(
            @Value("${ai.agent.openai.api-key}") String apiKey,
            @Value("${ai.agent.openai.model-name}") String modelName,
            @Value("${ai.agent.openai.timeout-seconds:60}") long timeoutSeconds) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException(
                    "OPENAI_API_KEY must be set when AGENT_AI_ENABLED=true");
        }
        return OpenAiChatModel.builder()
                .apiKey(apiKey)
                .modelName(modelName)
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .strictJsonSchema(true)
                .build();
    }

    @Bean
    @ConditionalOnProperty(name = "ai.agent.enabled", havingValue = "true")
    RiskAssessmentPort riskAssessmentPort(ChatModel agentChatModel, ObjectMapper objectMapper,
                                          AgentPerformanceMetrics performanceMetrics) {
        RiskAssessmentAiService service = AiServices.builder(RiskAssessmentAiService.class)
                .chatModel(agentChatModel)
                .build();
        return new LangChainRiskAssessmentAdapter(service, objectMapper, performanceMetrics);
    }

    @Bean
    @ConditionalOnProperty(name = "ai.agent.enabled", havingValue = "false", matchIfMissing = true)
    RiskAssessmentPort disabledRiskAssessmentPort() {
        return context -> { throw new BusinessException(SecurityEventErrorCode.AGENT_AI_DISABLED); };
    }
}


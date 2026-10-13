package com.omniguardy.backend.domain.securityevent.infrastructure.agent;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

class OpenAiAgentConfigTest {

    @Test
    void rejectsBlankApiKeyWhenAgentIsEnabled() {
        OpenAiAgentConfig config = new OpenAiAgentConfig();

        assertThrows(IllegalStateException.class,
                () -> config.agentChatModel(" ", "gpt-5-mini", 60));
    }
}

package com.omniguardy.backend.domain.tts.application.port.out;

public interface TtsDeliveryPort {
    void synthesizeAndPublish(String text);
}

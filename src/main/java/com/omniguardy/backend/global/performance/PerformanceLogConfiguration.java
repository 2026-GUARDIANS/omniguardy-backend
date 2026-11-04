package com.omniguardy.backend.global.performance;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PerformanceLogConfiguration {
    public PerformanceLogConfiguration(@Value("${omniguardy.performance.enabled:true}") boolean enabled,
                                       @Value("${omniguardy.performance.poll-sample-every:5}") int sampleEvery) {
        PerformanceLog.configure(enabled, sampleEvery);
    }
}

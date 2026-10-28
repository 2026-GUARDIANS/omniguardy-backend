package com.omniguardy.backend.global.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;

@Configuration
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name = "app.tts.enabled", havingValue = "true", matchIfMissing = true)
public class WebConfig implements WebMvcConfigurer {

    @Value("${app.tts.audio-dir}")
    private String audioDir;

    @Value("${app.tts.audio-url-prefix}")
    private String audioUrlPrefix;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String location = Path.of(audioDir)
                .toAbsolutePath()
                .normalize()
                .toUri()
                .toString();

        registry.addResourceHandler(audioUrlPrefix + "/**")
                .addResourceLocations(location);
    }
}
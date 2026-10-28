package com.omniguardy.backend.global.config;

import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.InitializingBean;
import java.net.URI;
import java.nio.charset.StandardCharsets;

@Component
@Profile("prod")
public class ProdConfigurationValidator implements InitializingBean {
    private final Environment env;
    public ProdConfigurationValidator(Environment env) { this.env = env; }
    @Override public void afterPropertiesSet() {
        for (String key : new String[]{"spring.datasource.url", "spring.datasource.username", "spring.datasource.password",
                "jwt.secret-key", "mqtt.username", "mqtt.password", "omniguardy.device.api-key",
                "firebase.credentials-path", "aws.region", "aws.s3.bucket", "aws.s3.video-prefix"}) {
            if (env.getRequiredProperty(key).isBlank()) throw new IllegalStateException("Required prod setting is blank: " + key);
        }
        if (env.getRequiredProperty("jwt.secret-key").getBytes(StandardCharsets.UTF_8).length < 32)
            throw new IllegalStateException("JWT_SECRET_KEY must contain at least 32 UTF-8 bytes");
        if (env.getRequiredProperty("omniguardy.demo.user-id", Long.class) <= 0)
            throw new IllegalStateException("OMNIGUARDY_DEMO_USERID must be a positive existing user ID");
        for (String type : new String[]{"audio", "vision"}) {
            URI uri = URI.create(env.getRequiredProperty("ai.fastapi." + type + ".base-url"));
            if (!"https".equals(uri.getScheme()) || uri.getHost() == null)
                throw new IllegalStateException("Prod FastAPI base URL must be HTTPS: " + type);
            for (String timeout : new String[]{"connect-timeout-seconds", "read-timeout-seconds"})
                if (env.getRequiredProperty("ai.fastapi." + type + "." + timeout, Long.class) <= 0)
                    throw new IllegalStateException("FastAPI timeout must be positive: " + type);
        }
    }
}

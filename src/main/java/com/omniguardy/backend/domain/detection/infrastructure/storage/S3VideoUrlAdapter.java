package com.omniguardy.backend.domain.detection.infrastructure.storage;

import com.omniguardy.backend.domain.securityevent.application.port.out.VideoUrlPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import java.time.Duration;

@Component
@Profile("prod")
public class S3VideoUrlAdapter implements VideoUrlPort {
    private final S3Presigner presigner;
    private final String bucket;
    private final String prefix;
    public S3VideoUrlAdapter(S3Presigner presigner, @Value("${aws.s3.bucket}") String bucket,
            @Value("${aws.s3.video-prefix}") String prefix) {
        this.presigner = presigner; this.bucket = bucket; this.prefix = prefix.replaceAll("^/+|/+$", "") + "/";
    }
    @Override public String sign(String key) {
        if (!key.startsWith(prefix) || key.contains("..")) throw new IllegalArgumentException("Not an archived video key");
        return presigner.presignGetObject(r -> r.signatureDuration(Duration.ofMinutes(5))
                .getObjectRequest(g -> g.bucket(bucket).key(key))).url().toString();
    }
}

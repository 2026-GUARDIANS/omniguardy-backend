package com.omniguardy.backend.domain.detection.infrastructure.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import java.time.Duration;

@Configuration
@Profile("prod")
public class S3StorageConfig {
    @Bean(destroyMethod = "close")
    DefaultCredentialsProvider awsCredentials() { return DefaultCredentialsProvider.builder().build(); }
    @Bean(destroyMethod = "close")
    S3Client s3Client(@Value("${aws.region}") String region, DefaultCredentialsProvider credentials) {
        return S3Client.builder().region(Region.of(region)).credentialsProvider(credentials)
                .httpClientBuilder(UrlConnectionHttpClient.builder()
                        .connectionTimeout(Duration.ofSeconds(5)).socketTimeout(Duration.ofSeconds(120)))
                .overrideConfiguration(c -> c.apiCallTimeout(Duration.ofSeconds(180)))
                .build();
    }
    @Bean(destroyMethod = "close")
    S3Presigner s3Presigner(@Value("${aws.region}") String region, DefaultCredentialsProvider credentials) {
        return S3Presigner.builder().region(Region.of(region)).credentialsProvider(credentials).build();
    }
}

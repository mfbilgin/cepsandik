package com.cepsandik.communityservice.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

/**
 * Topluluk logo yüklemeleri için AWS S3 yapılandırması.
 * user-service'teki S3Config ile aynı kalıbı izler; aynı bucket'ı kullanır.
 */
@Configuration
public class S3Config {

    @Value("${aws.s3.access-key:}")
    private String accessKey;

    @Value("${aws.s3.secret-key:}")
    private String secretKey;

    @Value("${aws.s3.region:eu-north-1}")
    private String region;

    @Bean
    public S3Client s3Client() {
        if (accessKey.isEmpty() || secretKey.isEmpty()) {
            // Varsayılan kimlik zinciri (EC2 instance role, env vars vb.)
            return S3Client.builder()
                    .region(Region.of(region))
                    .build();
        }

        return S3Client.builder()
                .region(Region.of(region))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(accessKey, secretKey)))
                .build();
    }
}

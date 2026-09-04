package com.giftmarket.global.storage.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

@Configuration
@EnableConfigurationProperties(S3Properties.class)
@ConditionalOnProperty(
        name = "storage.provider",
        havingValue = "s3"
)
public class S3Config {

    @Bean
    public S3Client s3Client(S3Properties properties) {
        AwsBasicCredentials credentials = AwsBasicCredentials.create(
                properties.accessKey(),
                properties.secretKey()
        );

        return S3Client.builder()
                .region(Region.of(properties.region()))
                .credentialsProvider(
                        StaticCredentialsProvider.create(credentials)
                )
                .httpClientBuilder(
                        UrlConnectionHttpClient.builder()
                )
                .build();
    }

    @Bean
    public S3Presigner s3Presigner(S3Properties properties) {
        AwsBasicCredentials credentials = AwsBasicCredentials.create(
                properties.accessKey(),
                properties.secretKey()
        );

        return S3Presigner.builder()
                .region(Region.of(properties.region()))
                .credentialsProvider(
                        StaticCredentialsProvider.create(credentials)
                )
                .build();
    }
}
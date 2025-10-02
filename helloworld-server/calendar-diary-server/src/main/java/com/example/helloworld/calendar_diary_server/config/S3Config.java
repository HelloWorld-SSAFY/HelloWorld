package com.example.helloworld.calendar_diary_server.config;

import lombok.Getter; import lombok.Setter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean; import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.*;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.util.Map;

// config/S3Config.java
@Configuration
@ConfigurationProperties(prefix = "app.s3")
@Getter @Setter
public class S3Config {
    private String bucket;
    private Map<String,String> path;
}


@Configuration
class AwsClientsConfig {
    @Bean
    S3Client s3(@Value("${spring.cloud.aws.region.static}") String region,
                @Value("${AWS_ACCESS_KEY_ID:}") String ak1,
                @Value("${AWS_SECRET_ACCESS_KEY:}") String sk1,
                @Value("${S3_ACCESSKEY:}") String ak2,
                @Value("${S3_SECRETKEY:}") String sk2) {
        String ak = !ak1.isBlank()? ak1 : ak2;
        String sk = !sk1.isBlank()? sk1 : sk2;
        var creds = ak.isBlank()
                ? DefaultCredentialsProvider.create()
                : StaticCredentialsProvider.create(AwsBasicCredentials.create(ak, sk));
        return S3Client.builder()
                .region(Region.of(region))
                .credentialsProvider(creds)
                .build();
    }

    @Bean
    S3Presigner presigner(@Value("${spring.cloud.aws.region.static}") String region,
                          @Value("${AWS_ACCESS_KEY_ID:}") String ak1,
                          @Value("${AWS_SECRET_ACCESS_KEY:}") String sk1,
                          @Value("${S3_ACCESSKEY:}") String ak2,
                          @Value("${S3_SECRETKEY:}") String sk2) {
        String ak = !ak1.isBlank()? ak1 : ak2;
        String sk = !sk1.isBlank()? sk1 : sk2;
        var creds = ak.isBlank()
                ? DefaultCredentialsProvider.create()
                : StaticCredentialsProvider.create(AwsBasicCredentials.create(ak, sk));
        return S3Presigner.builder()
                .region(Region.of(region))
                .credentialsProvider(creds)
                .build();
    }
}

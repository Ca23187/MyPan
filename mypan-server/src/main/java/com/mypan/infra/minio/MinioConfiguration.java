package com.mypan.infra.minio;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioAsyncClient;
import io.minio.MinioClient;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
@EnableConfigurationProperties(MinioProperties.class)
@ConditionalOnProperty(
        prefix = "storage",
        name = "type",
        havingValue = "minio"
)
@Slf4j
public class MinioConfiguration {

    @Bean
    public OkHttpClient minioOkHttpClient() {
        return new OkHttpClient.Builder()
                .connectTimeout(Duration.ofSeconds(10))
                .readTimeout(Duration.ofMinutes(5))
                .writeTimeout(Duration.ofMinutes(5))
                .callTimeout(Duration.ofMinutes(10))
                .build();
    }

    @Bean
    public MinioClient minioClient(MinioProperties properties, OkHttpClient minioOkHttpClient) {
        MinioClient client = MinioClient.builder()
                .endpoint(properties.getEndpoint())
                .credentials(properties.getAccessKey(), properties.getSecretKey())
                .httpClient(minioOkHttpClient)
                .build();

        // 启动时确保 bucket 存在
        if (properties.isAutoCreateBucket()) {
            ensureBucketExists(client, properties.getBucketName());
        }

        return client;
    }

    @Bean
    public MinioAsyncClient minioAsyncClient(MinioProperties properties, OkHttpClient minioOkHttpClient) {
        return MinioAsyncClient.builder()
                .endpoint(properties.getEndpoint())
                .credentials(properties.getAccessKey(), properties.getSecretKey())
                .httpClient(minioOkHttpClient)
                .build();
    }

    private void ensureBucketExists(MinioClient client, String bucket) {
        try {
            boolean exists = client.bucketExists(
                    BucketExistsArgs.builder().bucket(bucket).build()
            );
            if (!exists) {
                client.makeBucket(
                        MakeBucketArgs.builder().bucket(bucket).build()
                );
                log.info("MinIO bucket created: {}", bucket);
            }
        } catch (Exception e) {
            // 直接报清楚点，避免“默默失败然后运行时各种 500”
            throw new IllegalStateException("Failed to ensure MinIO bucket exists: " + bucket, e);
        }
    }
}

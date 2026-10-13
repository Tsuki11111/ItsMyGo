package com.tsuk1.itsmygo.config;

import io.minio.MinioClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MinioConfig {

    /**
     * 复用本机共用的 MinIO 实例（与 rag_helper 同一个），靠 bucket 隔离：
     * 本项目用 its-mygo-product-images，不许碰 rag_helper 的 rag-helper-knowledge-files。
     */
    @Bean
    public MinioClient minioClient(@Value("${itsmygo.minio.endpoint}") String endpoint,
                                   @Value("${itsmygo.minio.access-key}") String accessKey,
                                   @Value("${itsmygo.minio.secret-key}") String secretKey) {
        return MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .build();
    }
}

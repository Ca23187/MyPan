package com.mypan.infra.minio;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "minio")
@Data
public class MinioProperties {

    /** 内部访问 MinIO 的地址 */
    private String endpoint;

    /** 对外访问地址（用于预签名 URL）；没有就填和 endpoint 一样 */
    private String publicEndpoint;

    private String accessKey;
    private String secretKey;

    private String bucketName;

    /** 可选：统一前缀，避免桶内对象混杂，如 file/ */
    private String basePrefix = "";

    /** 可选：启动时自动创建 bucket */
    private boolean autoCreateBucket = true;
}

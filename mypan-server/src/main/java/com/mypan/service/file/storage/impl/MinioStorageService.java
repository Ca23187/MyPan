package com.mypan.service.file.storage.impl;

import com.mypan.common.exception.BusinessException;
import com.mypan.common.response.ResponseCodeEnum;
import com.mypan.common.utils.servlet.ContentTypeGuesser;
import com.mypan.infra.minio.MinioProperties;
import com.mypan.service.dto.responseWrite.ObjMeta;
import com.mypan.service.file.storage.ObjectStorageService;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import io.minio.*;
import io.minio.errors.ErrorResponseException;
import io.minio.messages.DeleteObject;
import io.minio.messages.Item;
import io.minio.messages.Part;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.InputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(
        prefix = "storage",
        name = "type",
        havingValue = "minio"
)
public class MinioStorageService implements ObjectStorageService {

    private final MinioClient minioClient;

    private final MinioAsyncClient minioAsyncClient;

    private final MinioProperties minioProperties;

    @Override
    public void save(String objectKey, InputStream in, long size, String contentType) {
        String key = applyBasePrefix(objectKey);
        // 写入时兜底：若调用方没传 contentType，则按后缀猜；猜不到就 octet-stream
        String ct = StringUtils.hasText(contentType) ? contentType : ContentTypeGuesser.guess(key);
        try {
            PutObjectArgs.Builder b = PutObjectArgs.builder()
                    .bucket(minioProperties.getBucketName())
                    .object(key)
                    .stream(in, size, -1)
                    .contentType(ct);
            minioClient.putObject(b.build());
        } catch (Exception e) {
            throw new BusinessException(ResponseCodeEnum.INTERNAL_ERROR);
        }
    }

    @Override
    public InputStream get(String objectKey) {
        String key = applyBasePrefix(objectKey);
        try {
            return minioClient.getObject(
                    GetObjectArgs.builder()
                            .bucket(minioProperties.getBucketName())
                            .object(key)
                            .build()
            );
        } catch (ErrorResponseException e) {
            if (isNoSuchKey(e)) {
                throw new BusinessException(ResponseCodeEnum.FILE_NOT_FOUND);
            }
            throw new BusinessException(ResponseCodeEnum.INTERNAL_ERROR);
        } catch (Exception e) {
            throw new BusinessException(ResponseCodeEnum.INTERNAL_ERROR);
        }
    }

    @Override
    public void delete(String objectKey) {
        String key = applyBasePrefix(objectKey);
        try {
            minioClient.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(minioProperties.getBucketName())
                            .object(key)
                            .build()
            );
        } catch (ErrorResponseException e) {
            if (isNoSuchKey(e)) return;
            throw new BusinessException(ResponseCodeEnum.INTERNAL_ERROR);
        } catch (Exception e) {
            throw new BusinessException(ResponseCodeEnum.INTERNAL_ERROR);
        }
    }

    @Override
    public void deleteByPrefix(String prefixUnderBase) {
        String bucket = minioProperties.getBucketName();
        String prefix = applyBasePrefix(prefixUnderBase);
        try {
            List<DeleteObject> toDelete = new ArrayList<>();
            Iterable<Result<Item>> results = minioClient.listObjects(
                    ListObjectsArgs.builder()
                            .bucket(bucket)
                            .prefix(prefix)
                            .recursive(true)
                            .build()
            );

            for (Result<Item> r : results) {
                Item item = r.get();
                if (item == null || !StringUtils.hasText(item.objectName())) continue;
                toDelete.add(new DeleteObject(item.objectName()));
            }
            if (toDelete.isEmpty()) return;
            Iterable<Result<io.minio.messages.DeleteError>> errors = minioClient.removeObjects(
                    RemoveObjectsArgs.builder()
                            .bucket(bucket)
                            .objects(toDelete)
                            .build()
            );

            for (Result<io.minio.messages.DeleteError> er : errors) {
                io.minio.messages.DeleteError de = er.get();
                if (de != null) {
                    log.warn("MinIO removeObjects error: object={}, code={}, message={}",
                            de.objectName(), de.code(), de.message());
                }
            }
        } catch (Exception e) {
            throw new BusinessException(ResponseCodeEnum.INTERNAL_ERROR);
        }
    }


    @Override
    public ObjMeta statIfExists(String objectKey) {
        String key = applyBasePrefix(objectKey);
        try {
            StatObjectResponse meta = minioClient.statObject(
                    StatObjectArgs.builder()
                            .bucket(minioProperties.getBucketName())
                            .object(key)
                            .build()
            );
            String ct = meta.contentType();
            if (!StringUtils.hasText(ct)) {
                ct = ContentTypeGuesser.guess(key);
            }
            return new ObjMeta(meta.size(), ct);
        } catch (ErrorResponseException e) {
            if (isNoSuchKey(e)) return null;
            log.warn("minio statObject failed, objectKey={}, key={}, code={}",
                    objectKey, key,
                    e.errorResponse() != null ? e.errorResponse().code() : null, e);
            throw new BusinessException(ResponseCodeEnum.INTERNAL_ERROR);
        } catch (Exception e) {
            log.warn("minio statObject exception, objectKey={}, key={}", objectKey, key, e);
            throw new BusinessException(ResponseCodeEnum.INTERNAL_ERROR);
        }
    }

    private String applyBasePrefix(String objectKey) {
        if (!StringUtils.hasText(objectKey))
            throw new BusinessException(ResponseCodeEnum.BAD_REQUEST);

        String base = minioProperties.getBasePrefix();
        if (!StringUtils.hasText(base)) return trimLeadingSlash(objectKey);

        String b = base.trim();
        if (!b.endsWith("/")) b = b + "/";

        String k = trimLeadingSlash(objectKey);
        return b + k;
    }

    private String trimLeadingSlash(String s) {
        String x = s;
        while (x.startsWith("/")) x = x.substring(1);
        return x;
    }

    private boolean isNoSuchKey(ErrorResponseException e) {
        if (e == null || e.errorResponse() == null) return false;
        String code = e.errorResponse().code();
        return "NoSuchKey".equals(code) || "NoSuchObject".equals(code) || "NotFound".equals(code);
    }

    @Override
    public String initiateMultipartUpload(String objectKey, String contentType) {
        String key = applyBasePrefix(objectKey);
        try {
            String ct = StringUtils.hasText(contentType) ?
                    contentType : ContentTypeGuesser.guess(key);

            Multimap<String, String> headers = ArrayListMultimap.create();
            headers.put("Content-Type", ct);
            CreateMultipartUploadResponse resp = minioAsyncClient
                    .createMultipartUploadAsync(
                            minioProperties.getBucketName(),
                            null,
                            key,
                            headers,
                            null
                    ).get();
            return resp.result().uploadId();
        } catch (Exception e) {
            throw new BusinessException(ResponseCodeEnum.INTERNAL_ERROR, e);
        }
    }

    @Override
    public String uploadPart(String objectKey, String uploadId, int partNumber, InputStream in, long size) {
        String key = applyBasePrefix(objectKey);
        try {
            UploadPartResponse resp = minioAsyncClient
                    .uploadPartAsync(
                            minioProperties.getBucketName(),
                            null,
                            key,
                            in,
                            size,
                            uploadId,
                            partNumber,
                            null,
                            null
                    ).get();
            return resp.etag();
        } catch (Exception e) {
            throw new BusinessException(ResponseCodeEnum.INTERNAL_ERROR, e);
        }
    }

    @Override
    public void completeMultipartUpload(String objectKey, String uploadId, List<CompletedPart> parts) {
        if (parts == null || parts.isEmpty()) throw new BusinessException(ResponseCodeEnum.BAD_REQUEST);
        String key = applyBasePrefix(objectKey);
        try {
            List<CompletedPart> sorted = parts.stream()
                    .sorted(Comparator.comparingInt(CompletedPart::partNumber))
                    .toList();
            Part[] minioParts = new Part[sorted.size()];
            for (int i = 0; i < sorted.size(); i++) {
                CompletedPart p = sorted.get(i);
                minioParts[i] = new Part(p.partNumber(), p.etag());
            }
            minioAsyncClient.completeMultipartUploadAsync(
                    minioProperties.getBucketName(),
                    null,
                    key,
                    uploadId,
                    minioParts,
                    null,
                    null
            ).get();
        } catch (Exception e) {
            throw new BusinessException(ResponseCodeEnum.INTERNAL_ERROR, e);
        }
    }

    @Override
    public void abortMultipartUpload(String objectKey, String uploadId) {
        if (!StringUtils.hasText(uploadId)) return;
        String key = applyBasePrefix(objectKey);
        try {
            minioAsyncClient.abortMultipartUploadAsync(
                    minioProperties.getBucketName(),
                    null,
                    key,
                    uploadId,
                    null,
                    null
            ).get();
        } catch (Exception ignored) {}
    }

    @Override
    public int abortStaleMultipartUploads(String prefixUnderBase, int olderThanHours, int maxScanned) {
        String bucket = minioProperties.getBucketName();
        String prefix;  // prefix 允许为空：空=扫 basePrefix 下全部（basePrefix 也可为空=扫全 bucket）
        if (StringUtils.hasText(prefixUnderBase)) {
            prefix = applyBasePrefix(prefixUnderBase); // 非空才用你现有校验
        } else {
            String base = minioProperties.getBasePrefix();
            if (!StringUtils.hasText(base)) {
                prefix = ""; // 扫全 bucket
            } else {
                String b = base.trim();
                if (!b.endsWith("/")) b = b + "/";
                prefix = trimLeadingSlash(b);
            }
        }

        Instant cutoff = Instant.now().minus(Duration.ofHours(Math.max(1, olderThanHours)));

        String keyMarker = null;
        String uploadIdMarker = null;

        int scanned = 0;
        int aborted = 0;

        while (scanned < Math.max(1, maxScanned)) {
            ListMultipartUploadsResponse resp;
            try {
                resp = minioAsyncClient.listMultipartUploadsAsync(
                        bucket,
                        null, // region
                        null, // delimiter
                        null, // encodingType
                        keyMarker,
                        Math.min(1000, maxScanned - scanned),
                        prefix,
                        uploadIdMarker,
                        null, // extraHeaders
                        null  // extraQueryParams
                ).get();
            } catch (Exception e) {
                log.warn("listMultipartUploadsAsync failed, bucket={}, prefix={}", bucket, prefix, e);
                break;
            }

            var result = resp.result();
            if (result == null || result.uploads() == null || result.uploads().isEmpty()) break;

            for (var u : result.uploads()) {
                scanned++;
                if (scanned > maxScanned) break;
                // 防 NPE + 用 Instant 对齐时区
                if (u.initiated() == null) continue;
                Instant initiated = u.initiated().toInstant();
                if (initiated.isAfter(cutoff)) continue;

                try {  // 直接 abort（objectName 用 SDK 返回值，避免 basePrefix 双拼）
                    minioAsyncClient.abortMultipartUploadAsync(
                            bucket,
                            null,
                            u.objectName(),
                            u.uploadId(),
                            null,
                            null
                    ).get();
                    aborted++;
                } catch (Exception e) {
                    log.warn("abortMultipartUploadAsync failed, object={}, uploadId={}",
                            u.objectName(), u.uploadId(), e);
                }
            }

            if (!result.isTruncated()) break;

            keyMarker = result.nextKeyMarker();
            uploadIdMarker = result.nextUploadIdMarker();

            if (!StringUtils.hasText(keyMarker) && !StringUtils.hasText(uploadIdMarker)) break;
        }

        return aborted;
    }

    @Override
    public InputStream getRange(String objectKey, long offset, long length) {
        if (offset < 0 || length <= 0) {
            throw new BusinessException(ResponseCodeEnum.BAD_REQUEST);
        }
        String key = applyBasePrefix(objectKey);

        try {
            return minioClient.getObject(
                    GetObjectArgs.builder()
                            .bucket(minioProperties.getBucketName())
                            .object(key)
                            .offset(offset)
                            .length(length)
                            .build()
            );
        } catch (Exception e) {
            throw new BusinessException(ResponseCodeEnum.INTERNAL_ERROR);
        }
    }
}

package com.mypan.service.file.storage;

import com.mypan.service.dto.responseWrite.ObjMeta;

import java.io.InputStream;
import java.util.List;

public interface ObjectStorageService extends BasicStorageService {

    InputStream getRange(String objectKey, long offset, long length);

    void deleteByPrefix(String prefixUnderBase);

    ObjMeta statIfExists(String objectKey);

    String initiateMultipartUpload(String objectKey, String contentType);

    /**
     * @return etag（后续 complete 需要）
     */
    String uploadPart(String objectKey, String uploadId, int partNumber, InputStream in, long size);

    void completeMultipartUpload(String objectKey, String uploadId, List<CompletedPart> parts);

    void abortMultipartUpload(String objectKey, String uploadId);

    record CompletedPart(int partNumber, String etag) {}

    /**
     * 清理（abort）超过一定时间仍未完成的 multipart upload
     * @param prefixUnderBase 仅扫描这个前缀下的 upload（建议传 "" 或 "202512/" 或 basePrefix 下的某段）
     * @param olderThanHours 只 abort initiated 早于该阈值的 upload，避免误伤正在上传的
     * @param maxScanned 本次最多扫描/处理多少个（防止一次跑太久）
     * @return abort 成功数量
     */
    int abortStaleMultipartUploads(String prefixUnderBase, int olderThanHours, int maxScanned);
}

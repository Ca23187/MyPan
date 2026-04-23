package com.mypan.service.schedule;

import com.mypan.infra.jpa.entity.FileInfo;

public interface FileCleanService {

    /**
     * 清理上传分片临时目录 + redis temp size key
     */
    void cleanupUploadTemp(String userId, String fileId);

    /**
     * 清理本地加工产物（合并后的成品文件、图片缩略图、视频封面、视频切片目录等）
     * 该方法用于：
     *  - Minio 完成转码并成功上传后，清除所有本地产物
     *  - 定时器任务中，定期清理卡在 transfer 或者变成 transfer_fail 状态的文件的所有残余产物
     *  注：清理不包括残余分片，后者由cleanupUploadTemp单独完成
     */
    void cleanupLocalFileArtifacts(FileInfo fileInfo);

    /**
     * 清理孤儿 temp 目录
     */
    void purgeOrphanTempFolders(int olderThanHours);

    /**
     * 兜底清理孤立的僵尸 .tmp 文件
     */
    void purgeLocalTmpFiles(int localTmpOlderThanHours);
}

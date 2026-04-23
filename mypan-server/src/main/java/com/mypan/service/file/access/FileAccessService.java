package com.mypan.service.file.access;

import com.mypan.service.dto.responseWrite.FileReadResourceDto;

public interface FileAccessService {

    /**
     * 统一打开：普通文件 / 视频m3u8 / ts 切片（内部含权限校验）
     */
    FileReadResourceDto openForRead(String fileIdOrSegment, String userId);

    /**
     * 打开缩略图/封面图等静态图片（路径参数校验）
     */
    FileReadResourceDto openThumbnailForRead(String imageFolder, String imageName);

    /** 只按 objectKey 打开，用于下载文件（支持分片） */
    FileReadResourceDto openForDownload(String objectKey);

}

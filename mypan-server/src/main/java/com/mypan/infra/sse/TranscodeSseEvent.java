package com.mypan.infra.sse;

public record TranscodeSseEvent(
        String type,      // "TRANSCODE_STATUS"
        String fileId,
        Integer status,   // 2=成功 1=失败 0=转码中(可选)
        String fileCover,
        Long fileSize
) {}

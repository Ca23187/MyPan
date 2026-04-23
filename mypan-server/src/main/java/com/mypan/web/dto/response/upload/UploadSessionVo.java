package com.mypan.web.dto.response.upload;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
public class UploadSessionVo implements Serializable {
    private String fileId;
    private String status;  // instant_upload / uploading
    private List<Integer> uploaded;  // 已上传 chunkIndex（0-based）
    private Boolean mpu;
    private String uploadId;  // MinIO multipart 可选
}

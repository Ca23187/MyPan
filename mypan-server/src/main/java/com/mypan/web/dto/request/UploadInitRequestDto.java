package com.mypan.web.dto.request;

import lombok.Data;

import java.io.Serializable;

@Data
public class UploadInitRequestDto implements Serializable {
    private String fileId;
    private String fileName;
    private String filePid;
    private String fileMd5;
    private Long fileSize;
    private Integer chunks;
    private Long chunkSize;
}

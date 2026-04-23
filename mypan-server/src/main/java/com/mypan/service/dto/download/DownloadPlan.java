package com.mypan.service.dto.download;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class DownloadPlan {
    public enum Type { SINGLE, ZIP }
    private Type type;

    // SINGLE
    private String objectKey;
    private String fileName;
    private String fileId;
    private String userId;
    private Long contentLength;
    private String contentType;

    // ZIP
    private String zipName;
    private List<ZipEntryResource> entries;
}
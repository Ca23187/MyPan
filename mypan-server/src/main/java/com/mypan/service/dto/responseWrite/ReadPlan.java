package com.mypan.service.dto.responseWrite;

import lombok.Data;

@Data
public final class ReadPlan {
    private String objectKey;
    private Long size;
    private String contentType;
}
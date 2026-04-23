package com.mypan.common.enums;


import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum FileStatusEnum {
    TRANSCODING(0, "Transcoding"),
    TRANSCODE_FAILED(1, "Transcode Failed"),
    ACTIVE(2, "Active");

    private final Integer status;
    private final String desc;
}

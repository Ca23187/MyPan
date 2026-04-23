package com.mypan.common.enums;


import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum UploadStatusEnum {
    INSTANT("instant_upload", "Instant Upload"),
    UPLOADING("uploading", "Uploading"),
    COMPLETED("upload_completed", "Completed"),
    ABORTED("aborted", "Aborted");

    private final String code;
    private final String desc;

}

package com.mypan.service.dto.download;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public final class ZipEntryResource {
    private String objectKey;  // storage key
    private String entryName;  // zip 内路径，如 "资料/子目录/a.txt"
    private boolean dir;       // 是否目录
    public static ZipEntryResource dir(String entryName) {
        ZipEntryResource e = new ZipEntryResource();
        e.dir = true;
        e.entryName = entryName.endsWith("/") ? entryName : (entryName + "/");
        e.objectKey = null;
        return e;
    }
}

package com.mypan.service.dto.responseWrite;

import lombok.Getter;
import lombok.Setter;

import java.io.InputStream;
import java.util.function.BiFunction;
import java.util.function.Supplier;

@Getter
@Setter
public final class FileReadResourceDto {
    /** 全量长度（必须） */
    private long contentLength;

    /** Content-Type（可空，最终会兜底 application/octet-stream） */
    private String contentType;

    /**
     * 全量读取：需要时再打开一个新的 InputStream（只能读一次，所以必须是 Supplier）
     */
    private Supplier<InputStream> openStream;

    /**
     * Range 读取：openRange.apply(offset, length) -> 新的 InputStream
     * 如果实现不支持 Range，可以为 null（但音频元数据/播放器会受影响）
     */
    private BiFunction<Long, Long, InputStream> openRange;

    public boolean supportsRange() {
        return openRange != null && contentLength > 0;
    }
}
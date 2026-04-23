package com.mypan.common.utils.servlet;

import org.springframework.util.StringUtils;

import java.util.Locale;
import java.util.Map;

public final class ContentTypeGuesser {

    private static final Map<String, String> EXT_TO_CT = Map.ofEntries(
            // images
            Map.entry("png", "image/png"),
            Map.entry("jpg", "image/jpeg"),
            Map.entry("jpeg", "image/jpeg"),
            Map.entry("gif", "image/gif"),
            Map.entry("webp", "image/webp"),
            // video
            Map.entry("mp4", "video/mp4"),
            Map.entry("m3u8", "application/vnd.apple.mpegurl"),
            Map.entry("ts", "video/mp2t"),
            Map.entry("mov", "video/quicktime"),
            Map.entry("mkv", "video/x-matroska"),
            Map.entry("webm", "video/webm"),
            // audio
            Map.entry("mp3", "audio/mpeg"),
            Map.entry("wav", "audio/wav"),
            Map.entry("flac", "audio/flac"),
            Map.entry("aac", "audio/aac"),
            Map.entry("ogg", "audio/ogg"),
            Map.entry("m4a", "audio/mp4"),
            // docs
            Map.entry("pdf", "application/pdf"),
            Map.entry("txt", "text/plain"),
            Map.entry("json", "application/json"),
            Map.entry("html", "text/html"),
            Map.entry("css", "text/css"),
            Map.entry("js", "application/javascript"),
            // archives
            Map.entry("zip", "application/zip"),
            Map.entry("7z", "application/x-7z-compressed"),
            Map.entry("rar", "application/vnd.rar")
    );

//    /** contentType -> ext（主后缀） */
//    private static final Map<String, String> CT_TO_EXT;
//
//    static {
//        Map<String, String> m = new HashMap<>();
//        // 如果一个 contentType 对应多个 ext，后面的会覆盖前面的
//        // 这里的策略是：**你认为哪个是主格式，就让它最后 put**
//        for (Map.Entry<String, String> e : EXT_TO_CT.entrySet()) {
//            m.put(e.getValue(), e.getKey());
//        }
//        CT_TO_EXT = Collections.unmodifiableMap(m);
//    }

    private ContentTypeGuesser() {}

    public static String guess(String pathOrName) {
        if (!StringUtils.hasText(pathOrName)) {
            return "application/octet-stream";
        }
        int idx = pathOrName.lastIndexOf('.');
        if (idx < 0 || idx == pathOrName.length() - 1) {
            return "application/octet-stream";
        }
        String ext = pathOrName.substring(idx + 1).toLowerCase(Locale.ROOT);
        return EXT_TO_CT.getOrDefault(ext, "application/octet-stream");
    }

//    /** ✅ 新增：从 contentType 反推后缀（用于 ffmpeg / ffprobe / tmp file） */
//    public static String extFromContentType(String contentType) {
//        if (!StringUtils.hasText(contentType)) return "";
//        return CT_TO_EXT.getOrDefault(contentType.toLowerCase(Locale.ROOT), "");
//    }
}

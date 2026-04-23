package com.mypan.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@Getter
@AllArgsConstructor
public enum FileTypeEnum {

    VIDEO(FileCategoryEnum.VIDEO, 1, new String[]{".mp4", ".avi", ".rmvb", ".mkv", ".mov"}, "Video"),
    AUDIO(FileCategoryEnum.AUDIO, 2, new String[]{".mp3", ".wav", ".wma", ".mp2", ".flac", ".midi", ".ra", ".ape", ".aac", ".cda"}, "Audio"),
    IMAGE(FileCategoryEnum.IMAGE, 3, new String[]{".jpeg", ".jpg", ".png", ".gif", ".bmp", ".dds", ".psd", ".pdt", ".webp", ".xmp", ".svg", ".tiff"}, "Image"),
    PDF(FileCategoryEnum.DOC, 4, new String[]{".pdf"}, "pdf"),
    WORD(FileCategoryEnum.DOC, 5, new String[]{".docx", ".doc"}, "Word"),
    EXCEL(FileCategoryEnum.DOC, 6, new String[]{".xlsx", ".xls", ".csv"}, "Excel"),
    TXT(FileCategoryEnum.DOC, 7, new String[]{".txt"}, "Txt"),
    PROGRAM(FileCategoryEnum.OTHERS, 8, new String[]{".h", ".c", ".hpp", ".hxx", ".cpp", ".cc", ".c++", ".cxx", ".m", ".o", ".s", ".dll", ".cs",
            ".java", ".class", ".js", ".ts", ".css", ".scss", ".vue", ".jsx", ".sql", ".md", ".json", ".html", ".xml", ".sh", ".bat", ".yml", ".yaml",
            ".ini", ".conf", ".properties", ".env", ".log"}, "Code"),
    ZIP(FileCategoryEnum.OTHERS, 9, new String[]{".rar", ".zip", ".7z", ".cab", ".arj", ".lzh", ".tar", ".gz", ".ace", ".uue", ".bz", ".jar", ".iso", ".mpq"}, "Zip"),
    OTHERS(FileCategoryEnum.OTHERS, 10, new String[]{}, "Others");

    private final FileCategoryEnum category;
    private final Integer type;
    private final String[] suffixes;
    private final String desc;

    /** type -> enum 的静态映射（O(1) 查找） */
    private static final Map<Integer, FileTypeEnum> TYPE_MAP;

    /** suffix -> enum 的静态映射（O(1) 查找） */
    private static final Map<String, FileTypeEnum> SUFFIX_MAP;

    static {
        // type map
        Map<Integer, FileTypeEnum> typeMap = new HashMap<>();
        for (FileTypeEnum e : FileTypeEnum.values()) {
            if (typeMap.put(e.type, e) != null) {
                throw new IllegalStateException("Duplicate type: " + e.type);
            }
        }
        TYPE_MAP = Collections.unmodifiableMap(typeMap);

        // suffix map
        Map<String, FileTypeEnum> suffixMap = new HashMap<>();
        for (FileTypeEnum e : FileTypeEnum.values()) {
            for (String s : e.suffixes) {
                String key = normalizeSuffix(s);
                FileTypeEnum old = suffixMap.put(key, e);
                if (old != null && old != e) {
                    throw new IllegalStateException("Duplicate suffix: " + key + ", old=" + old + ", new=" + e);
                }
            }
        }
        SUFFIX_MAP = Collections.unmodifiableMap(suffixMap);
    }

    private static String normalizeSuffix(String suffix) {
        if (suffix == null) return null;
        String s = suffix.trim().toLowerCase();
        if (s.isEmpty()) return s;
        return s.startsWith(".") ? s : "." + s;
    }

    public static FileTypeEnum getBySuffix(String suffix) {
        if (suffix == null) return FileTypeEnum.OTHERS;
        FileTypeEnum hit = SUFFIX_MAP.get(normalizeSuffix(suffix));
        return hit == null ? FileTypeEnum.OTHERS : hit;
    }

    public static FileTypeEnum getByType(Integer type) {
        return type == null ? null : TYPE_MAP.get(type);
    }

    public static FileTypeEnum getByFilename(String filename) {
        if (filename == null) return FileTypeEnum.OTHERS;
        int idx = filename.lastIndexOf('.');
        if (idx < 0 || idx == filename.length() - 1) return FileTypeEnum.OTHERS;
        return getBySuffix(filename.substring(idx));
    }
}


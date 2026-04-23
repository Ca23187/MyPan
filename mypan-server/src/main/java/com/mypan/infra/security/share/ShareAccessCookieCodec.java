package com.mypan.infra.security.share;

import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;

public class ShareAccessCookieCodec {
    private ShareAccessCookieCodec() {}

    /** 解析 share_access cookie value -> (shareId -> accessKey) */
    public static Map<String, String> parse(String cookieVal) {
        Map<String, String> map = new LinkedHashMap<>();
        if (!StringUtils.hasText(cookieVal)) return map;

        String[] parts = cookieVal.split("\\|");
        for (String p : parts) {
            if (!StringUtils.hasText(p)) continue;
            int idx = p.indexOf(':');
            if (idx <= 0 || idx >= p.length() - 1) continue;

            String shareId = p.substring(0, idx).trim();
            String accessKey = p.substring(idx + 1).trim();
            if (!shareId.isEmpty() && !accessKey.isEmpty()) {
                map.put(shareId, accessKey);
            }
        }
        return map;
    }

    /** map -> cookie value，最多保留 maxEntries（按 map 迭代顺序） */
    public static String serialize(Map<String, String> map, int maxEntries) {
        if (map == null || map.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        int count = 0;
        for (Map.Entry<String, String> e : map.entrySet()) {
            if (count >= maxEntries) break;
            if (!sb.isEmpty()) sb.append('|');
            sb.append(e.getKey()).append(':').append(e.getValue());
            count++;
        }
        return sb.toString();
    }

    /** 写入/更新 shareId->accessKey，并把该条目移动到最前（最近使用） */
    public static String upsert(String oldCookieVal, String shareId, String accessKey, int maxEntries) {
        Map<String, String> old = parse(oldCookieVal);

        Map<String, String> neu = new LinkedHashMap<>();
        neu.put(shareId, accessKey); // 放到最前
        for (Map.Entry<String, String> e : old.entrySet()) {
            if (neu.containsKey(e.getKey())) continue;
            neu.put(e.getKey(), e.getValue());
        }
        return serialize(neu, maxEntries);
    }

    /** 从 cookieVal 里取某个 shareId 对应的 accessKey */
    public static String getAccessKey(String cookieVal, String shareId) {
        if (shareId == null) return null;
        return parse(cookieVal).get(shareId);
    }

    /** 从 cookieVal 中删除某个 shareId 条目 */
    public static String remove(String oldCookieVal, String shareId, int maxEntries) {
        if (shareId == null) return oldCookieVal == null ? "" : oldCookieVal;
        Map<String, String> old = parse(oldCookieVal);
        if (!old.containsKey(shareId)) {
            return oldCookieVal == null ? "" : oldCookieVal;
        }
        old.remove(shareId);
        return serialize(old, maxEntries);
    }
}
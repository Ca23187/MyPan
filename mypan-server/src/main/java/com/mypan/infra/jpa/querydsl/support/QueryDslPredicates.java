package com.mypan.infra.jpa.querydsl.support;

import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.dsl.DateTimePath;
import com.querydsl.core.types.dsl.SimpleExpression;
import com.querydsl.core.types.dsl.StringExpression;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * QueryDSL 动态条件拼装工具（仅负责 where 条件的“积木”）
 */
public final class QueryDslPredicates {

    private QueryDslPredicates() {}

    /** 非字符串通用：仅判 null */
    public static <T> void andEq(BooleanBuilder b, SimpleExpression<T> path, T value) {
        if (b == null || path == null) return;
        if (value != null) b.and(path.eq(value));
    }

    public static <T> void andNotEq(BooleanBuilder b, SimpleExpression<T> path, T value) {
        if (b == null || path == null) return;
        if (value != null) b.and(path.ne(value));
    }

    /** 字符串专用：判 hasText（trim 后 eq） */
    public static void andEqText(BooleanBuilder b, StringExpression path, String value) {
        if (b == null || path == null) return;
        if (StringUtils.hasText(value)) b.and(path.eq(value.trim()));
    }

    /** contains / containsIgnoreCase（带 LIKE 简单转义） */
    public static void andContains(BooleanBuilder b, StringExpression path, String raw, boolean ignoreCase) {
        if (b == null || path == null) return;
        if (!StringUtils.hasText(raw)) return;
        String v = raw.trim();
        b.and(ignoreCase ? path.containsIgnoreCase(v) : path.contains(v));
    }

    /** startsWith / startsWithIgnoreCase（通常用于 ID 类前缀匹配） */
    public static void andStartsWith(BooleanBuilder b, StringExpression path, String raw, boolean ignoreCase) {
        if (b == null || path == null) return;
        if (!StringUtils.hasText(raw)) return;
        String v = raw.trim();
        b.and(ignoreCase ? path.startsWithIgnoreCase(v) : path.startsWith(v));
    }

    /** endsWith / endsWithIgnoreCase（一般少用，必要时可启用） */
    public static void andEndsWith(BooleanBuilder b, StringExpression path, String raw, boolean ignoreCase) {
        if (b == null || path == null) return;
        if (!StringUtils.hasText(raw)) return;
        String v = raw.trim();
        b.and(ignoreCase ? path.endsWithIgnoreCase(v) : path.endsWith(v));
    }

    /**
     * 时间闭区间 [start, end]；若仅有一端，则单端过滤。
     * 约定：startStr/endStr 是可被 fmt 解析的日期时间字符串。
     */
    public static void betweenDateTime(BooleanBuilder b,
                                       DateTimePath<LocalDateTime> path,
                                       String startStr,
                                       String endStr,
                                       DateTimeFormatter fmt) {
        if (b == null || path == null) return;

        LocalDateTime start = parseLdt(startStr, fmt);
        LocalDateTime end = parseLdt(endStr, fmt);

        if (start != null && end != null) {
            b.and(path.between(start, end));
        } else if (start != null) {
            b.and(path.goe(start));
        } else if (end != null) {
            // 保持你原先习惯：仅给 end 时，扩到当天末尾
            b.and(path.loe(end.with(LocalTime.MAX)));
        }
    }

    /** 解析 LocalDateTime；解析失败返回 null（避免请求参数错误直接 500） */
    public static LocalDateTime parseLdt(String s, DateTimeFormatter fmt) {
        if (!StringUtils.hasText(s) || fmt == null) return null;
        try {
            return LocalDateTime.parse(s.trim(), fmt);
        } catch (Exception e) {
            return null;
        }
    }

    /** in 条件：arr 非空才拼；自动过滤空字符串（仅适用于 String[] 这类） */
    public static void inIfNotEmpty(BooleanBuilder b, StringExpression path, String[] arr) {
        if (b == null || path == null) return;
        if (arr == null || arr.length == 0) return;
        List<String> list = Arrays.stream(arr)
                .filter(StringUtils::hasText)
                .map(String::trim)
                .toList();
        if (!list.isEmpty()) b.and(path.in(list));
    }

    /** not in 条件：arr 非空才拼；自动过滤空字符串（仅适用于 String[] 这类） */
    public static void notInIfNotEmpty(BooleanBuilder b, StringExpression path, String[] arr) {
        if (b == null || path == null) return;
        if (arr == null || arr.length == 0) return;
        List<String> list = Arrays.stream(arr)
                .filter(StringUtils::hasText)
                .map(String::trim)
                .toList();
        if (!list.isEmpty()) b.and(path.notIn(list));
    }

    /** in 条件：泛型版本（不做 trim/过滤），用于 Integer[]/Long[]/Enum[] 等 */
    public static <T> void inIfNotEmpty(BooleanBuilder b, SimpleExpression<T> path, T[] arr) {
        if (b == null || path == null) return;
        if (arr != null && arr.length > 0) b.and(path.in(Arrays.asList(arr)));
    }

    /** not in 条件：泛型版本（不做 trim/过滤），用于 Integer[]/Long[]/Enum[] 等 */
    public static <T> void notInIfNotEmpty(BooleanBuilder b, SimpleExpression<T> path, T[] arr) {
        if (b == null || path == null) return;
        if (arr != null && arr.length > 0) b.and(path.notIn(Arrays.asList(arr)));
    }

    /**
     * eqIgnoreCase：字符串等值忽略大小写
     * （注意：是否走索引取决于数据库 collation/函数索引）
     */
    public static void andEqIgnoreCase(BooleanBuilder b, StringExpression path, String value) {
        if (b == null || path == null) return;
        if (!StringUtils.hasText(value)) return;
        b.and(path.equalsIgnoreCase(value.trim()));
    }

    /** in 条件：Collection<String> 版本（过滤空串 + trim），适用于 List<String>/Set<String> */
    public static void inIfNotEmpty(BooleanBuilder b, StringExpression path, Collection<String> values) {
        if (b == null || path == null) return;
        if (values == null || values.isEmpty()) return;

        List<String> list = values.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .toList();

        if (!list.isEmpty()) b.and(path.in(list));
    }

    /** not in 条件：Collection<String> 版本（过滤空串 + trim） */
    public static void notInIfNotEmpty(BooleanBuilder b, StringExpression path, Collection<String> values) {
        if (b == null || path == null) return;
        if (values == null || values.isEmpty()) return;

        List<String> list = values.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .toList();

        if (!list.isEmpty()) b.and(path.notIn(list));
    }

    /** in 条件：泛型 Collection 版本（不做 trim/过滤），用于 Integer/Long/Enum 等 */
    public static <T> void inIfNotEmpty(BooleanBuilder b, SimpleExpression<T> path, Collection<T> values) {
        if (b == null || path == null) return;
        if (values == null || values.isEmpty()) return;

        // 轻量防御：过滤 null，避免生成 in(null, ...) 导致行为不确定
        List<T> list = values.stream().filter(Objects::nonNull).toList();
        if (!list.isEmpty()) b.and(path.in(list));
    }

    /** not in 条件：泛型 Collection 版本（不做 trim/过滤） */
    public static <T> void notInIfNotEmpty(BooleanBuilder b, SimpleExpression<T> path, Collection<T> values) {
        if (b == null || path == null) return;
        if (values == null || values.isEmpty()) return;

        List<T> list = values.stream().filter(Objects::nonNull).toList();
        if (!list.isEmpty()) b.and(path.notIn(list));
    }

}

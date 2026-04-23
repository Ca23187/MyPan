package com.mypan.common.utils.json;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

@Slf4j
public final class JsonUtils {

    private JsonUtils() {}
    
    private static final ObjectMapper mapper;

    static {
        mapper = new ObjectMapper();
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        mapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
        mapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
        mapper.enable(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT);
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        mapper.registerModule(new JavaTimeModule());
    }

    /** 对象 → JSON 字符串（严格版，错误抛异常） */
    public static String toJson(Object obj) {
        if (obj == null) return "null";
        try {
            return mapper.writeValueAsString(obj);
        } catch (Exception e) {
            throw new RuntimeException("JSON 序列化失败", e);
        }
    }

    /** 对象 → JSON（宽松版，异常返回 null） */
    public static String toJsonOrNull(Object obj) {
        if (obj == null) return null;
        try {
            return mapper.writeValueAsString(obj);
        } catch (Exception e) {
            log.error("对象转 JSON 出错", e);
            return null;
        }
    }

    /** JSON → 对象（严格版：抛异常） */
    public static <T> T toObj(String json, Class<T> clazz) {
        if (!StringUtils.hasText(json)) return null;
        try {
            return mapper.readValue(json, clazz);
        } catch (Exception e) {
            throw new RuntimeException("JSON 反序列化失败：" + json, e);
        }
    }

    /** JSON → 对象（宽松版：返回 null） */
    public static <T> T toObjOrNull(String json, Class<T> clazz) {
        if (!StringUtils.hasText(json)) return null;
        try {
            return mapper.readValue(json, clazz);
        } catch (Exception e) {
            log.error("JSON 转对象失败，json={}", json, e);
            return null;
        }
    }

    /** JSON → List （严格版） */
    public static <T> List<T> toList(String json, Class<T> clazz) {
        if (!StringUtils.hasText(json)) return new ArrayList<>();
        try {
            JavaType type = mapper.getTypeFactory().constructCollectionType(List.class, clazz);
            return mapper.readValue(json, type);
        } catch (Exception e) {
            throw new RuntimeException("JSON 转 List 失败：" + json, e);
        }
    }

    /** JSON → 复杂类型（严格版） */
    public static <T> T toObj(String json, TypeReference<T> typeRef) {
        if (!StringUtils.hasText(json)) return null;
        try {
            return mapper.readValue(json, typeRef);
        } catch (Exception e) {
            throw new RuntimeException("JSON 转复杂对象失败：" + json, e);
        }
    }

    public static ObjectMapper mapper() {
        return mapper;
    }
}


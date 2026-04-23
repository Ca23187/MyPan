package com.mypan.infra.redis;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
@Slf4j
public class RedisUtils {

    /**
     * 统一只用 StringRedisTemplate：
     * - String：计数/锁/脚本/状态位
     * - Object：序列化成纯 JSON 字符串
     */
    private final StringRedisTemplate strRedis;
    private final ObjectMapper objectMapper;

    /* ------------------------------------------------------------------------
     * Common
     * ------------------------------------------------------------------------ */

    public void delete(String... keys) {
        if (keys == null || keys.length == 0) {
            return;
        }
        try {
            strRedis.delete(Arrays.asList(keys));
        } catch (Exception e) {
            log.error("redis delete failed, keys={}", Arrays.toString(keys), e);
        }
    }

    public boolean exists(String key) {
        if (!StringUtils.hasText(key)) {
            return false;
        }
        try {
            return Boolean.TRUE.equals(strRedis.hasKey(key));
        } catch (Exception e) {
            log.error("redis exists failed, key={}", key, e);
            return false;
        }
    }

    public boolean expire(String key, long time, TimeUnit unit) {
        if (!StringUtils.hasText(key) || time <= 0 || unit == null) {
            return false;
        }
        try {
            return Boolean.TRUE.equals(strRedis.expire(key, time, unit));
        } catch (Exception e) {
            log.error("redis expire failed, key={}, time={}, unit={}", key, time, unit, e);
            return false;
        }
    }

    /* ------------------------------------------------------------------------
     * String operations
     * 约定：
     * 1. String value 不保存 null
     * 2. set(key, null) 等价于 delete(key)
     * ------------------------------------------------------------------------ */

    public String get(String key) {
        if (!StringUtils.hasText(key)) {
            return null;
        }
        try {
            return strRedis.opsForValue().get(key);
        } catch (Exception e) {
            log.error("redis get string failed, key={}", key, e);
            return null;
        }
    }

    public boolean set(String key, String value) {
        return setEx(key, value, 0, null);
    }

    public boolean setEx(String key, String value, long time, TimeUnit unit) {
        if (!StringUtils.hasText(key)) {
            return false;
        }
        try {
            if (value == null) {
                strRedis.delete(key);
                return true;
            }

            if (time > 0 && unit != null) {
                strRedis.opsForValue().set(key, value, time, unit);
            } else {
                strRedis.opsForValue().set(key, value);
            }
            return true;
        } catch (Exception e) {
            log.error("redis setEx string failed, key={}, time={}, unit={}", key, time, unit, e);
            return false;
        }
    }

    public Long incrBy(String key, long delta) {
        if (!StringUtils.hasText(key)) {
            return null;
        }
        try {
            return strRedis.opsForValue().increment(key, delta);
        } catch (Exception e) {
            log.error("redis incrBy failed, key={}, delta={}", key, delta, e);
            return null;
        }
    }

    /**
     * 仅在 key 首次创建时设置过期时间。
     * 非严格原子；若用于强一致限流，建议改 Lua。
     */
    public Long incrByWithExpireOnFirstCreate(String key, long delta, long time, TimeUnit unit) {
        if (!StringUtils.hasText(key)) {
            return null;
        }
        try {
            Long val = strRedis.opsForValue().increment(key, delta);
            if (val == null) {
                return null;
            }
            if (time > 0 && unit != null && val == delta) {
                strRedis.expire(key, time, unit);
            }
            return val;
        } catch (Exception e) {
            log.error("redis incrByWithExpireOnFirstCreate failed, key={}, delta={}", key, delta, e);
            return null;
        }
    }

    public boolean setnxEx(String key, String value, long time, TimeUnit unit) {
        if (!StringUtils.hasText(key)) {
            return false;
        }
        try {
            if (value == null) {
                return false;
            }

            Boolean ok;
            if (time > 0 && unit != null) {
                ok = strRedis.opsForValue().setIfAbsent(key, value, time, unit);
            } else {
                ok = strRedis.opsForValue().setIfAbsent(key, value);
            }
            return Boolean.TRUE.equals(ok);
        } catch (Exception e) {
            log.error("redis setnxEx failed, key={}, time={}, unit={}", key, time, unit, e);
            return false;
        }
    }

    /* ------------------------------------------------------------------------
     * Hash String operations
     * 约定：
     * 1. hash field 不允许 null
     * 2. value 为 null 时，删除该 field
     * ------------------------------------------------------------------------ */

    public boolean hset(String key, String field, String value) {
        if (!StringUtils.hasText(key) || field == null) {
            return false;
        }
        try {
            if (value == null) {
                strRedis.opsForHash().delete(key, field);
                return true;
            }
            strRedis.opsForHash().put(key, field, value);
            return true;
        } catch (Exception e) {
            log.error("redis hset string failed, key={}, field={}", key, field, e);
            return false;
        }
    }

    public Map<String, String> hgetAll(String key) {
        if (!StringUtils.hasText(key)) {
            return Map.of();
        }
        try {
            Map<Object, Object> raw = strRedis.opsForHash().entries(key);
            if (raw.isEmpty()) {
                return Map.of();
            }

            Map<String, String> res = new HashMap<>(raw.size());
            for (Map.Entry<Object, Object> e : raw.entrySet()) {
                if (e.getKey() == null) {
                    continue;
                }
                res.put(String.valueOf(e.getKey()), e.getValue() == null ? null : String.valueOf(e.getValue()));
            }
            return res;
        } catch (Exception e) {
            log.error("redis hgetAll string failed, key={}", key, e);
            return Map.of();
        }
    }

    public Long hlen(String key) {
        if (!StringUtils.hasText(key)) {
            return null;
        }
        try {
            return strRedis.opsForHash().size(key);
        } catch (Exception e) {
            log.error("redis hlen failed, key={}", key, e);
            return null;
        }
    }

    /* ------------------------------------------------------------------------
     * Object / DTO operations (Pure JSON string)
     * 约定：
     * 1. Redis 中不保存 Object null
     * 2. set(key, null) 等价于 delete(key)
     * ------------------------------------------------------------------------ */

    public <T> T get(String key, Class<T> clazz) {
        if (!StringUtils.hasText(key) || clazz == null) {
            return null;
        }
        try {
            String json = strRedis.opsForValue().get(key);
            if (json == null) {
                return null;
            }
            return readObject(json, clazz);
        } catch (Exception e) {
            log.error("redis get object failed, key={}, clazz={}", key, clazz.getName(), e);
            return null;
        }
    }

    public <T> T get(String key, TypeReference<T> typeRef) {
        if (!StringUtils.hasText(key) || typeRef == null) {
            return null;
        }
        try {
            String json = strRedis.opsForValue().get(key);
            if (json == null) {
                return null;
            }
            return readObject(json, typeRef);
        } catch (Exception e) {
            log.error("redis get(TypeRef) failed, key={}", key, e);
            return null;
        }
    }

    public boolean set(String key, Object value) {
        return setEx(key, value, 0, null);
    }

    public boolean setEx(String key, Object value, long time, TimeUnit unit) {
        if (!StringUtils.hasText(key)) {
            return false;
        }
        try {
            if (value == null) {
                strRedis.delete(key);
                return true;
            }

            String json = writeObject(value);
            if (time > 0 && unit != null) {
                strRedis.opsForValue().set(key, json, time, unit);
            } else {
                strRedis.opsForValue().set(key, json);
            }
            return true;
        } catch (Exception e) {
            log.error("redis setEx object failed, key={}, time={}, unit={}", key, time, unit, e);
            return false;
        }
    }

    /* ------------------------------------------------------------------------
     * Lua Script helpers (ARGV 全是纯字符串)
     * ------------------------------------------------------------------------ */

    public Long execLongWithStrArgs(DefaultRedisScript<Long> script, List<String> keys, String... args) {
        if (script == null) {
            return null;
        }
        try {
            return strRedis.execute(script, keys == null ? Collections.emptyList() : keys, (Object[]) args);
        } catch (Exception e) {
            log.error("redis execLongWithStrArgs failed, keys={}, args={}", keys, Arrays.toString(args), e);
            return null;
        }
    }

    public boolean delIfValueEquals(String key, String expectedValue) {
        if (!StringUtils.hasText(key) || expectedValue == null) {
            return false;
        }

        String scriptText = """
            if redis.call('get', KEYS[1]) == ARGV[1] then
                return redis.call('del', KEYS[1])
            else
                return 0
            end
        """;

        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setScriptText(scriptText);
        script.setResultType(Long.class);

        Long r = execLongWithStrArgs(script, Collections.singletonList(key),
                expectedValue);

        return r != null && r > 0;
    }

    /* ------------------------------------------------------------------------
     * Private helpers
     * ------------------------------------------------------------------------ */

    private String writeObject(Object value) throws IOException {
        return objectMapper.writeValueAsString(value);
    }

    private <T> T readObject(String json, Class<T> clazz) throws IOException {
        return objectMapper.readValue(json, clazz);
    }

    private <T> T readObject(String json, TypeReference<T> typeRef) throws IOException {
        return objectMapper.readValue(json, typeRef);
    }
}

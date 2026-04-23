package com.mypan.common.enums;


import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@Getter
@AllArgsConstructor
public enum ShareExpireTypeEnum {
    DAY_1(0, 1, "1 day"),
    DAY_7(1, 7, "7 days"),
    DAY_30(2, 30, "30 days"),
    FOREVER(3, -1, "Never expires");

    private final Integer type;
    private final Integer days;
    private final String desc;

    /** category -> enum 的静态映射（O(1) 查找） */
    private static final Map<Integer, ShareExpireTypeEnum> TYPE_MAP;

    static {
        Map<Integer, ShareExpireTypeEnum> map = new HashMap<>();
        for (ShareExpireTypeEnum e : ShareExpireTypeEnum.values()) {
            map.put(e.type, e);
        }
        TYPE_MAP = Collections.unmodifiableMap(map);
    }

    public static ShareExpireTypeEnum getByType(Integer type) {
        return type == null ? null : TYPE_MAP.get(type);
    }

}

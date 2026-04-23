package com.mypan.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@Getter
@AllArgsConstructor
public enum SearchScopeEnum {

    SCOPE_CURRENT_ONLY(0),
    SCOPE_CURRENT_RECURSIVE(1),
    SCOPE_ALL(2);

    private final Integer type;

    /** type -> enum 映射（O(1) 查找） */
    private static final Map<Integer, SearchScopeEnum> TYPE_MAP;

    static {
        Map<Integer, SearchScopeEnum> map = new HashMap<>();
        for (SearchScopeEnum e : SearchScopeEnum.values()) {
            map.put(e.type, e);
        }
        TYPE_MAP = Collections.unmodifiableMap(map);
    }

    public static SearchScopeEnum getByType(Integer type) {
        return type == null ? null : TYPE_MAP.get(type);
    }

    public static SearchScopeEnum ofOrDefault(Integer type, SearchScopeEnum def) {
        SearchScopeEnum e = getByType(type);
        return e == null ? def : e;
    }
}

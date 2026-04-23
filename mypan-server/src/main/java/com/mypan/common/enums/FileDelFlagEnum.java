package com.mypan.common.enums;


import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum FileDelFlagEnum {
    DELETED(0, "Deleted"),
    RECYCLED(1, "Recycled"),
    ACTIVE(2, "Active"),
    RECYCLED_CHILD(3, "Recycled Child");

    private final Integer flag;
    private final String desc;
}

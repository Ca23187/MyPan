package com.mypan.common.enums;


import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum UserStatusEnum {

    DISABLED(0, "Disabled"),
    ACTIVE(1, "Active");

    private final Integer status;
    private final String desc;

}

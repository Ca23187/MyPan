package com.mypan.web.dto.query;

import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;

@Getter
@Setter
public class BaseQuery implements Serializable {
    // 分页排序
    private Integer pageNo;
    private Integer pageSize;
    private String orderKey;
}

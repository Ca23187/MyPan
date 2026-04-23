package com.mypan.web.dto.response;

import lombok.Getter;

import java.io.Serializable;
import java.util.List;

@Getter
public final class PaginationResultVo<T> implements Serializable {
	private Long totalCount;
	private Integer pageSize;
	private Integer pageNo;
	private Integer pageTotal;
	private final List<T> list;

	public PaginationResultVo(Long totalCount, Integer pageSize, Integer pageNo, List<T> list) {
		this.totalCount = totalCount;
		this.pageSize = pageSize;
		this.pageNo = pageNo;
		this.list = list;
	}

    public PaginationResultVo(Long totalCount, Integer pageSize, Integer pageNo, Integer pageTotal, List<T> list) {
        if (pageNo == 0) {
            pageNo = 1;
        }
        this.totalCount = totalCount;
        this.pageSize = pageSize;
        this.pageNo = pageNo;
        this.pageTotal = pageTotal;
        this.list = list;
    }

	public PaginationResultVo(List<T> list) {
		this.list = list;
	}
}

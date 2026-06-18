package com.mdplatform.management.dto;

import lombok.Data;

import java.util.List;

@Data
public class PageResponse<T> {

    private long total;
    private int page;
    private int pageSize;
    private List<T> list;

    public static <T> PageResponse<T> of(long total, int page, int pageSize, List<T> list) {
        PageResponse<T> response = new PageResponse<>();
        response.setTotal(total);
        response.setPage(page);
        response.setPageSize(pageSize);
        response.setList(list);
        return response;
    }
}

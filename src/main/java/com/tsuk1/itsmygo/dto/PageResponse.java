package com.tsuk1.itsmygo.dto;

import com.baomidou.mybatisplus.core.metadata.IPage;

import java.util.List;

public record PageResponse<T>(List<T> records, long total, long page, long size) {

    public static <T> PageResponse<T> of(IPage<T> source) {
        return new PageResponse<>(source.getRecords(), source.getTotal(), source.getCurrent(), source.getSize());
    }
}

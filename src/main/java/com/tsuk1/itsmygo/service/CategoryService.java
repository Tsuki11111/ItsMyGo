package com.tsuk1.itsmygo.service;

import com.tsuk1.itsmygo.dto.CategoryNode;
import com.tsuk1.itsmygo.dto.CategoryRequest;
import com.tsuk1.itsmygo.entity.Category;

import java.util.List;

public interface CategoryService {

    /** @param onlyEnabled 顾客端传 true（只看启用），管理端传 false */
    List<CategoryNode> tree(boolean onlyEnabled);

    Category create(CategoryRequest request);

    Category update(Long id, CategoryRequest request);

    void delete(Long id);
}

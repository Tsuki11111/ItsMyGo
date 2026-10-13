package com.tsuk1.itsmygo.dto;

import java.util.List;

public record CategoryNode(
        Long id,
        Long parentId,
        String name,
        Integer sort,
        Integer status,
        List<CategoryNode> children
) {
}

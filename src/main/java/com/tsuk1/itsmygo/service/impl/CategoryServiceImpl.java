package com.tsuk1.itsmygo.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.tsuk1.itsmygo.common.ResultCode;
import com.tsuk1.itsmygo.common.exception.BizException;
import com.tsuk1.itsmygo.dto.CategoryNode;
import com.tsuk1.itsmygo.dto.CategoryRequest;
import com.tsuk1.itsmygo.entity.Category;
import com.tsuk1.itsmygo.entity.Product;
import com.tsuk1.itsmygo.mapper.CategoryMapper;
import com.tsuk1.itsmygo.mapper.ProductMapper;
import com.tsuk1.itsmygo.service.CategoryService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class CategoryServiceImpl implements CategoryService {

    private static final long TOP_LEVEL = 0L;

    private final CategoryMapper categoryMapper;
    private final ProductMapper productMapper;

    public CategoryServiceImpl(CategoryMapper categoryMapper, ProductMapper productMapper) {
        this.categoryMapper = categoryMapper;
        this.productMapper = productMapper;
    }

    /** 一次查全表再在内存里组树，避免按层递归查库 */
    @Override
    public List<CategoryNode> tree(boolean onlyEnabled) {
        List<Category> all = categoryMapper.selectList(Wrappers.<Category>lambdaQuery()
                .eq(onlyEnabled, Category::getStatus, 1)
                .orderByAsc(Category::getSort)
                .orderByAsc(Category::getId));

        Map<Long, List<Category>> byParent = all.stream()
                .collect(Collectors.groupingBy(Category::getParentId));

        return buildNodes(TOP_LEVEL, byParent);
    }

    private List<CategoryNode> buildNodes(Long parentId, Map<Long, List<Category>> byParent) {
        return byParent.getOrDefault(parentId, List.of()).stream()
                .map(c -> new CategoryNode(c.getId(), c.getParentId(), c.getName(), c.getSort(), c.getStatus(),
                        buildNodes(c.getId(), byParent)))
                .toList();
    }

    @Override
    public Category create(CategoryRequest request) {
        Category category = new Category();
        apply(category, request);
        categoryMapper.insert(category);
        return category;
    }

    @Override
    public Category update(Long id, CategoryRequest request) {
        Category category = requireExists(id);
        apply(category, request);
        if (id.equals(category.getParentId())) {
            throw new BizException(ResultCode.BAD_REQUEST, "不能把自己设为父分类");
        }
        categoryMapper.updateById(category);
        return category;
    }

    @Override
    public void delete(Long id) {
        requireExists(id);

        boolean hasChildren = categoryMapper.exists(
                Wrappers.<Category>lambdaQuery().eq(Category::getParentId, id));
        if (hasChildren) {
            throw new BizException(ResultCode.BAD_REQUEST, "该分类下还有子分类，请先处理");
        }

        boolean hasProducts = productMapper.exists(
                Wrappers.<Product>lambdaQuery().eq(Product::getCategoryId, id));
        if (hasProducts) {
            throw new BizException(ResultCode.BAD_REQUEST, "该分类下还有商品，请先处理");
        }

        categoryMapper.deleteById(id);
    }

    private void apply(Category category, CategoryRequest request) {
        Long parentId = request.parentId() == null ? TOP_LEVEL : request.parentId();
        if (parentId != TOP_LEVEL) {
            requireExists(parentId);
        }
        category.setParentId(parentId);
        category.setName(request.name());
        category.setSort(request.sort() == null ? 0 : request.sort());
        category.setStatus(Boolean.FALSE.equals(request.enabled()) ? 0 : 1);
    }

    private Category requireExists(Long id) {
        Category category = categoryMapper.selectById(id);
        if (category == null) {
            throw new BizException(ResultCode.NOT_FOUND, "分类不存在");
        }
        return category;
    }
}

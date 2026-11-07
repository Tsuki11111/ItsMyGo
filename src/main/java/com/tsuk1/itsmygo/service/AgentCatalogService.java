package com.tsuk1.itsmygo.service;

import com.tsuk1.itsmygo.dto.CategoryNode;
import com.tsuk1.itsmygo.dto.agent.AgentActionView;
import com.tsuk1.itsmygo.dto.agent.AgentAddressEditRequest;
import com.tsuk1.itsmygo.dto.agent.AgentAddressView;
import com.tsuk1.itsmygo.dto.agent.AgentProductSearchRequest;
import com.tsuk1.itsmygo.dto.agent.AgentProductView;

import java.util.List;

/**
 * agent 需要的平台数据：商品搜索 / 语义索引全量、分类树、收货地址。
 *
 * <p>只有 {@link #updateAddress} 是写操作（address_edit 表单专用），
 * 归属校验复用 {@code AddressService.update}，越权一律按「不存在」返回 404。
 */
public interface AgentCatalogService {

    List<AgentProductView> searchProducts(AgentProductSearchRequest request);

    /** 语义索引用：当前在售商品全量（上限 100） */
    List<AgentProductView> listAll(int limit);

    List<CategoryNode> categories();

    List<AgentAddressView> addresses(Long userId);

    /** address_edit 表单提交后更新当前用户自己的收货地址，返回动作视图（summary.address 供 agent 读） */
    AgentActionView updateAddress(long userId, AgentAddressEditRequest request);
}

package com.tsuk1.itsmygo.service;

import com.tsuk1.itsmygo.dto.CategoryNode;
import com.tsuk1.itsmygo.dto.agent.AgentAddressView;
import com.tsuk1.itsmygo.dto.agent.AgentProductSearchRequest;
import com.tsuk1.itsmygo.dto.agent.AgentProductView;

import java.util.List;

/** agent 需要的只读平台数据：商品搜索 / 语义索引全量、分类树、收货地址。 */
public interface AgentCatalogService {

    List<AgentProductView> searchProducts(AgentProductSearchRequest request);

    /** 语义索引用：当前在售商品全量（上限 100） */
    List<AgentProductView> listAll(int limit);

    List<CategoryNode> categories();

    List<AgentAddressView> addresses(Long userId);
}

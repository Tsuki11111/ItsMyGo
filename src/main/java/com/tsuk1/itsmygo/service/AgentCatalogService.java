package com.tsuk1.itsmygo.service;

import com.tsuk1.itsmygo.dto.agent.AgentAddressView;
import com.tsuk1.itsmygo.dto.agent.AgentProductView;

import java.util.List;

/** agent 需要的只读平台数据：商品搜索、收货地址。 */
public interface AgentCatalogService {

    List<AgentProductView> searchProducts(String keyword, int limit);

    List<AgentAddressView> addresses(Long userId);
}

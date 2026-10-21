package com.tsuk1.itsmygo.service.impl;

import com.tsuk1.itsmygo.common.ResultCode;
import com.tsuk1.itsmygo.common.exception.BizException;
import com.tsuk1.itsmygo.dto.agent.AgentAddressView;
import com.tsuk1.itsmygo.dto.agent.AgentProductView;
import com.tsuk1.itsmygo.entity.Address;
import com.tsuk1.itsmygo.entity.Product;
import com.tsuk1.itsmygo.service.AddressService;
import com.tsuk1.itsmygo.service.AgentCatalogService;
import com.tsuk1.itsmygo.service.ProductService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Objects;

/** agent 的只读数据入口：商品搜索（带 SKU）与当前用户的收货地址。 */
@Service
public class AgentCatalogServiceImpl implements AgentCatalogService {

    private final ProductService productService;
    private final AddressService addressService;

    public AgentCatalogServiceImpl(ProductService productService, AddressService addressService) {
        this.productService = productService;
        this.addressService = addressService;
    }

    @Override
    public List<AgentProductView> searchProducts(String keyword, int limit) {
        String kw = keyword == null ? "" : keyword.trim();
        if (!StringUtils.hasText(kw)) {
            throw new BizException(ResultCode.BAD_REQUEST, "请提供商品关键词");
        }
        int safeLimit = Math.min(Math.max(limit, 1), 10);
        return productService.page(1, safeLimit, null, kw, true, null, true).records().stream()
                .map(this::toProductView)
                .filter(Objects::nonNull)
                .toList();
    }

    @Override
    public List<AgentAddressView> addresses(Long userId) {
        return addressService.list(userId).stream()
                .map(address -> new AgentAddressView(
                        address.getId(),
                        nullToEmpty(address.getReceiver()),
                        nullToEmpty(address.getPhone()),
                        fullAddress(address),
                        address.getIsDefault() != null && address.getIsDefault() == 1))
                .toList();
    }

    private AgentProductView toProductView(Product product) {
        try {
            Product detail = productService.detailForCustomer(product.getId());
            List<AgentProductView.Sku> skus = detail.getSkus() == null ? List.of()
                    : detail.getSkus().stream()
                    .filter(sku -> sku.getStatus() == null || sku.getStatus() == 1)
                    .map(sku -> new AgentProductView.Sku(
                            sku.getId(),
                            nullToEmpty(sku.getSpecText()),
                            money(sku.getPrice()),
                            sku.getStock()))
                    .toList();
            return new AgentProductView(
                    detail.getId(),
                    nullToEmpty(detail.getName()),
                    nullToEmpty(detail.getSubtitle()),
                    nullToEmpty(detail.getMainImage()),
                    money(detail.getPrice()),
                    detail.getHasSpecs() != null && detail.getHasSpecs() == 1,
                    skus);
        } catch (BizException e) {
            // 分页到详情之间被下架 / 删除：跳过这一条，不要让整个搜索失败
            return null;
        }
    }

    private String fullAddress(Address address) {
        return nullToEmpty(address.getProvince()) + nullToEmpty(address.getCity())
                + nullToEmpty(address.getDistrict()) + nullToEmpty(address.getDetail());
    }

    private String money(BigDecimal value) {
        return value == null ? "0.00" : value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}

package com.tsuk1.itsmygo.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.tsuk1.itsmygo.common.ResultCode;
import com.tsuk1.itsmygo.common.exception.BizException;
import com.tsuk1.itsmygo.dto.agent.AgentOrderDraftRequest;
import com.tsuk1.itsmygo.entity.Address;
import com.tsuk1.itsmygo.entity.Merchant;
import com.tsuk1.itsmygo.entity.Product;
import com.tsuk1.itsmygo.entity.ProductSku;
import com.tsuk1.itsmygo.mapper.AddressMapper;
import com.tsuk1.itsmygo.mapper.MerchantMapper;
import com.tsuk1.itsmygo.mapper.ProductMapper;
import com.tsuk1.itsmygo.mapper.ProductSkuMapper;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 把 agent 的下单参数校验、合并、重新计价成一张可持久化的草稿。 */
@Component
public class AgentOrderDraftBuilder {

    private final AddressMapper addressMapper;
    private final ProductMapper productMapper;
    private final ProductSkuMapper skuMapper;
    private final MerchantMapper merchantMapper;
    private final AgentActionSupport support;

    public AgentOrderDraftBuilder(AddressMapper addressMapper,
                                  ProductMapper productMapper,
                                  ProductSkuMapper skuMapper,
                                  MerchantMapper merchantMapper,
                                  AgentActionSupport support) {
        this.addressMapper = addressMapper;
        this.productMapper = productMapper;
        this.skuMapper = skuMapper;
        this.merchantMapper = merchantMapper;
        this.support = support;
    }

    public OrderDraft build(long userId, AgentOrderDraftRequest request) {
        if (request == null || request.items() == null || request.items().isEmpty()) {
            throw new BizException(ResultCode.BAD_REQUEST, "请指定要购买的商品");
        }
        if (request.items().size() > 20) {
            throw new BizException(ResultCode.BAD_REQUEST, "一次最多准备 20 行商品");
        }
        Address address = resolveAddress(userId, request.addressId());

        // 同一 SKU 的重复行先合并，避免用拆行绕过库存校验
        Map<Long, Integer> quantityBySku = new LinkedHashMap<>();
        Map<Long, Long> productBySku = new LinkedHashMap<>();
        for (AgentOrderDraftRequest.Item item : request.items()) {
            if (item == null || item.productId() == null || item.skuId() == null) {
                throw new BizException(ResultCode.BAD_REQUEST, "商品与规格不能为空");
            }
            int quantity = item.quantity() == null ? 0 : item.quantity();
            if (quantity < 1 || quantity > 99) {
                throw new BizException(ResultCode.BAD_REQUEST, "购买数量需在 1–99 之间");
            }
            quantityBySku.merge(item.skuId(), quantity, Integer::sum);
            productBySku.put(item.skuId(), item.productId());
        }

        Map<Long, Product> products = productMapper.selectByIds(
                        productBySku.values().stream().distinct().toList()).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));
        Map<Long, ProductSku> skus = skuMapper.selectByIds(
                        quantityBySku.keySet().stream().toList()).stream()
                .collect(Collectors.toMap(ProductSku::getId, Function.identity()));

        List<Line> lines = new ArrayList<>();
        for (Map.Entry<Long, Integer> entry : quantityBySku.entrySet()) {
            long skuId = entry.getKey();
            int quantity = entry.getValue();
            long productId = productBySku.get(skuId);
            Product product = products.get(productId);
            ProductSku sku = skus.get(skuId);
            if (product == null || sku == null || !support.productIdEquals(product, sku)) {
                throw new BizException(ResultCode.NOT_FOUND, "商品或规格不存在");
            }
            if (product.getStatus() == null || product.getStatus() != 1) {
                throw new BizException(ResultCode.BAD_REQUEST, "商品已下架：" + product.getName());
            }
            if (sku.getStatus() != null && sku.getStatus() != 1) {
                throw new BizException(ResultCode.BAD_REQUEST, "该规格已停售：" + product.getName());
            }
            if (sku.getStock() == null || sku.getStock() < quantity) {
                throw new BizException(ResultCode.BAD_REQUEST,
                        "库存不足：" + product.getName() + support.specSuffix(sku));
            }
            BigDecimal unitPrice = sku.getPrice() == null ? BigDecimal.ZERO : sku.getPrice();
            lines.add(new Line(product, sku, quantity, unitPrice,
                    unitPrice.multiply(BigDecimal.valueOf(quantity))));
        }

        Map<Long, List<Line>> groups = new LinkedHashMap<>();
        for (Line line : lines) {
            groups.computeIfAbsent(line.product().getMerchantId(), key -> new ArrayList<>()).add(line);
        }
        Map<Long, Merchant> merchants = merchantMapper.selectByIds(
                        groups.keySet().stream().toList()).stream()
                .collect(Collectors.toMap(Merchant::getId, Function.identity()));

        BigDecimal total = BigDecimal.ZERO;
        List<Map<String, Object>> groupViews = new ArrayList<>();
        for (Map.Entry<Long, List<Line>> entry : groups.entrySet()) {
            BigDecimal subtotal = entry.getValue().stream().map(Line::subtotal)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            total = total.add(subtotal);
            Merchant merchant = merchants.get(entry.getKey());
            Map<String, Object> group = new LinkedHashMap<>();
            group.put("merchantId", entry.getKey());
            group.put("merchantName", merchant == null ? "" : support.nullToEmpty(merchant.getName()));
            group.put("items", entry.getValue().stream().map(this::lineView).toList());
            group.put("subtotal", support.money(subtotal));
            group.put("discount", "0.00");
            group.put("total", support.money(subtotal));
            groupViews.add(group);
        }

        Map<String, Object> summary = new LinkedHashMap<>();
        Map<String, Object> addressView = new LinkedHashMap<>();
        addressView.put("id", address.getId());
        addressView.put("receiver", support.nullToEmpty(address.getReceiver()));
        addressView.put("phone", support.nullToEmpty(address.getPhone()));
        addressView.put("address", support.fullAddress(address));
        summary.put("address", addressView);
        summary.put("groups", groupViews);
        summary.put("itemCount", lines.stream().mapToInt(Line::quantity).sum());
        summary.put("originalAmount", support.money(total));
        summary.put("discountAmount", "0.00");
        summary.put("totalAmount", support.money(total));
        summary.put("note", "确认后生成待支付订单，支付请在订单详情页完成；智能下单暂不支持优惠券。");

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("addressId", address.getId());
        payload.put("items", lines.stream().map(line -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("productId", line.product().getId());
            item.put("skuId", line.sku().getId());
            item.put("quantity", line.quantity());
            return item;
        }).toList());
        return new OrderDraft(payload, summary);
    }

    private Map<String, Object> lineView(Line line) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("productId", line.product().getId());
        view.put("skuId", line.sku().getId());
        view.put("productName", support.nullToEmpty(line.product().getName()));
        view.put("specText", support.nullToEmpty(line.sku().getSpecText()));
        view.put("quantity", line.quantity());
        view.put("unitPrice", support.money(line.unitPrice()));
        view.put("subtotal", support.money(line.subtotal()));
        view.put("image", StringUtils.hasText(line.sku().getImage())
                ? line.sku().getImage() : support.nullToEmpty(line.product().getMainImage()));
        return view;
    }

    private Address resolveAddress(long userId, Long addressId) {
        Address address;
        if (addressId != null) {
            address = addressMapper.selectById(addressId);
            if (address == null || !support.userIdEquals(userId, address.getUserId())) {
                throw new BizException(ResultCode.NOT_FOUND, "收货地址不存在");
            }
        } else {
            address = addressMapper.selectOne(Wrappers.<Address>lambdaQuery()
                    .eq(Address::getUserId, userId)
                    .orderByDesc(Address::getIsDefault)
                    .orderByDesc(Address::getId)
                    .last("LIMIT 1"));
            if (address == null) {
                throw new BizException(ResultCode.BAD_REQUEST, "请先在用户中心添加收货地址");
            }
        }
        return address;
    }

    public record OrderDraft(Map<String, Object> payload, Map<String, Object> summary) {
    }

    private record Line(Product product, ProductSku sku, int quantity,
                        BigDecimal unitPrice, BigDecimal subtotal) {
    }
}

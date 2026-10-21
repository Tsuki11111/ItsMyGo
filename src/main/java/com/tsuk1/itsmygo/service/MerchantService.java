package com.tsuk1.itsmygo.service;

import com.tsuk1.itsmygo.dto.AdminMerchantView;
import com.tsuk1.itsmygo.dto.MerchantRequest;
import com.tsuk1.itsmygo.dto.MerchantView;
import com.tsuk1.itsmygo.dto.PageResponse;
import com.tsuk1.itsmygo.dto.ShipAddressRequest;
import com.tsuk1.itsmygo.entity.Merchant;

/** 商户入驻、资料与平台审核 */
public interface MerchantService {

    /** 顾客提交/重新提交入驻申请 */
    MerchantView apply(Long userId, MerchantRequest request);

    /** 当前用户的申请状态；没有申请返回 null */
    MerchantView myApplication(Long userId);

    /** 公开店铺信息；只有 ACTIVE 店铺可见 */
    MerchantView publicShop(Long merchantId);

    /** 商家自己的资料（要求店铺 ACTIVE） */
    MerchantView profile(Long merchantId);

    MerchantView updateProfile(Long merchantId, MerchantRequest request);

    /** 商家 / 平台自营设置发货地址；lng/lat 缺一即视为未定位 */
    MerchantView updateShipAddress(Long merchantId, ShipAddressRequest request);

    PageResponse<AdminMerchantView> pageForAdmin(int page, int size, String status, String keyword);

    void approve(Long merchantId);

    void reject(Long merchantId, String reason);

    void disable(Long merchantId);

    void enable(Long merchantId);

    /** 其他模块使用：店铺不存在/非 ACTIVE 一律 404 */
    Merchant requireActive(Long merchantId);
}

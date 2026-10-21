package com.tsuk1.itsmygo.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tsuk1.itsmygo.common.ResultCode;
import com.tsuk1.itsmygo.common.Roles;
import com.tsuk1.itsmygo.common.exception.BizException;
import com.tsuk1.itsmygo.dto.AdminMerchantView;
import com.tsuk1.itsmygo.dto.MerchantRequest;
import com.tsuk1.itsmygo.dto.MerchantView;
import com.tsuk1.itsmygo.dto.PageResponse;
import com.tsuk1.itsmygo.dto.ShipAddressRequest;
import com.tsuk1.itsmygo.entity.Merchant;
import com.tsuk1.itsmygo.entity.User;
import com.tsuk1.itsmygo.mapper.MerchantMapper;
import com.tsuk1.itsmygo.mapper.UserMapper;
import com.tsuk1.itsmygo.security.AuthIdentityService;
import com.tsuk1.itsmygo.service.MerchantService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class MerchantServiceImpl implements MerchantService {

    private static final int MAX_PAGE_SIZE = 100;

    private final MerchantMapper merchantMapper;
    private final UserMapper userMapper;
    private final AuthIdentityService authIdentityService;

    public MerchantServiceImpl(MerchantMapper merchantMapper, UserMapper userMapper,
                               AuthIdentityService authIdentityService) {
        this.merchantMapper = merchantMapper;
        this.userMapper = userMapper;
        this.authIdentityService = authIdentityService;
    }

    @Override
    @Transactional
    public MerchantView apply(Long userId, MerchantRequest request) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException(ResultCode.NOT_FOUND, "用户不存在");
        }
        if (Roles.MERCHANT.equals(user.getRole())) {
            throw new BizException(ResultCode.BAD_REQUEST, "该账号已是商家");
        }

        Merchant existing = merchantMapper.selectOne(Wrappers.<Merchant>lambdaQuery()
                .eq(Merchant::getOwnerUserId, userId));
        if (existing != null) {
            return switch (existing.getStatus()) {
                case Merchant.PENDING -> throw new BizException(ResultCode.BAD_REQUEST, "入驻申请审核中");
                case Merchant.ACTIVE -> throw new BizException(ResultCode.BAD_REQUEST, "该账号已是商家");
                case Merchant.DISABLED -> throw new BizException(ResultCode.BAD_REQUEST, "店铺已被禁用");
                default -> resubmit(existing.getId(), request);
            };
        }

        Merchant merchant = new Merchant();
        applyFields(merchant, request);
        merchant.setStatus(Merchant.PENDING);
        merchant.setOwnerUserId(userId);
        merchant.setRejectReason("");
        try {
            merchantMapper.insert(merchant);
        } catch (DuplicateKeyException e) {
            throw new BizException(ResultCode.BAD_REQUEST, "店铺名已被占用");
        }
        return toView(merchant);
    }

    private MerchantView resubmit(Long merchantId, MerchantRequest request) {
        Merchant update = new Merchant();
        applyFields(update, request);
        update.setStatus(Merchant.PENDING);
        update.setRejectReason("");
        int rows = merchantMapper.update(update, Wrappers.<Merchant>lambdaUpdate()
                .eq(Merchant::getId, merchantId)
                .eq(Merchant::getStatus, Merchant.REJECTED));
        if (rows == 0) {
            throw new BizException(ResultCode.BAD_REQUEST, "申请状态已变化，请刷新后重试");
        }
        return toView(merchantMapper.selectById(merchantId));
    }

    @Override
    public MerchantView myApplication(Long userId) {
        Merchant merchant = merchantMapper.selectOne(Wrappers.<Merchant>lambdaQuery()
                .eq(Merchant::getOwnerUserId, userId));
        return merchant == null ? null : toView(merchant);
    }

    @Override
    public MerchantView publicShop(Long merchantId) {
        Merchant merchant = merchantMapper.selectById(merchantId);
        if (merchant == null || !Merchant.ACTIVE.equals(merchant.getStatus())) {
            throw new BizException(ResultCode.NOT_FOUND, "店铺不存在");
        }
        // 公开店铺页不能泄露发货仓地址 / 坐标
        return toView(merchant, false);
    }

    @Override
    public MerchantView profile(Long merchantId) {
        return toView(requireActive(merchantId));
    }

    @Override
    @Transactional
    public MerchantView updateProfile(Long merchantId, MerchantRequest request) {
        Merchant merchant = requireActive(merchantId);
        Merchant update = new Merchant();
        applyFields(update, request);
        try {
            merchantMapper.update(update, Wrappers.<Merchant>lambdaUpdate()
                    .eq(Merchant::getId, merchant.getId()));
        } catch (DuplicateKeyException e) {
            throw new BizException(ResultCode.BAD_REQUEST, "店铺名已被占用");
        }
        return toView(merchantMapper.selectById(merchantId));
    }

    @Override
    @Transactional
    public MerchantView updateShipAddress(Long merchantId, ShipAddressRequest request) {
        requireMerchant(merchantId);
        // 经纬度必须成对；缺一个就清空，物流回退虚拟转运中心
        BigDecimal lng = request.lng();
        BigDecimal lat = request.lat();
        if (lng == null || lat == null) {
            lng = null;
            lat = null;
        }
        merchantMapper.update(null, Wrappers.<Merchant>lambdaUpdate()
                .eq(Merchant::getId, merchantId)
                .set(Merchant::getShipProvince, trim(request.province()))
                .set(Merchant::getShipCity, trim(request.city()))
                .set(Merchant::getShipDistrict, trim(request.district()))
                .set(Merchant::getShipAddress, trim(request.address()))
                .set(Merchant::getShipLng, lng)
                .set(Merchant::getShipLat, lat));
        return toView(merchantMapper.selectById(merchantId));
    }

    @Override
    public PageResponse<AdminMerchantView> pageForAdmin(int page, int size, String status, String keyword) {
        Page<Merchant> result = merchantMapper.selectPage(
                new Page<>(normalizePage(page), normalizeSize(size)),
                Wrappers.<Merchant>lambdaQuery()
                        .eq(StringUtils.hasText(status), Merchant::getStatus, status)
                        .like(StringUtils.hasText(keyword), Merchant::getName, keyword)
                        .orderByDesc(Merchant::getId));

        List<Merchant> merchants = result.getRecords();
        List<Long> ownerIds = merchants.stream().map(Merchant::getOwnerUserId)
                .filter(java.util.Objects::nonNull).distinct().toList();
        Map<Long, User> users = ownerIds.isEmpty() ? Map.of()
                : userMapper.selectByIds(ownerIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));

        List<AdminMerchantView> views = merchants.stream()
                .map(merchant -> toAdminView(merchant,
                        merchant.getOwnerUserId() == null ? null
                                : users.get(merchant.getOwnerUserId())))
                .toList();
        return new PageResponse<>(views, result.getTotal(), result.getCurrent(), result.getSize());
    }

    @Override
    @Transactional
    public void approve(Long merchantId) {
        Merchant merchant = requireMerchant(merchantId);
        if (Merchant.PLATFORM_MERCHANT_ID == merchantId) {
            throw new BizException(ResultCode.BAD_REQUEST, "平台自营店铺不能审核");
        }
        if (!Merchant.PENDING.equals(merchant.getStatus())) {
            throw new BizException(ResultCode.BAD_REQUEST, "该申请不在待审核状态");
        }
        Long ownerId = merchant.getOwnerUserId();
        if (ownerId == null) {
            throw new BizException(ResultCode.BAD_REQUEST, "申请缺少店主账号");
        }
        User owner = userMapper.selectById(ownerId);
        if (owner == null || Integer.valueOf(0).equals(owner.getStatus())) {
            throw new BizException(ResultCode.BAD_REQUEST, "店主账号不存在或已被禁用");
        }
        if (owner.getMerchantId() != null && !merchantId.equals(owner.getMerchantId())) {
            throw new BizException(ResultCode.BAD_REQUEST, "该账号已绑定其他店铺");
        }
        if (merchantMapper.approveIfPending(merchantId) == 0) {
            throw new BizException(ResultCode.BAD_REQUEST, "申请状态已变化，请刷新后重试");
        }

        User update = new User();
        update.setId(ownerId);
        update.setRole(Roles.MERCHANT);
        update.setMerchantId(merchantId);
        userMapper.updateById(update);
        authIdentityService.evict(ownerId);
    }

    @Override
    @Transactional
    public void reject(Long merchantId, String reason) {
        Merchant merchant = requireMerchant(merchantId);
        if (Merchant.PLATFORM_MERCHANT_ID == merchantId) {
            throw new BizException(ResultCode.BAD_REQUEST, "平台自营店铺不能审核");
        }
        if (!Merchant.PENDING.equals(merchant.getStatus())) {
            throw new BizException(ResultCode.BAD_REQUEST, "该申请不在待审核状态");
        }
        if (merchantMapper.rejectIfPending(merchantId, reason) == 0) {
            throw new BizException(ResultCode.BAD_REQUEST, "申请状态已变化，请刷新后重试");
        }
        authIdentityService.evict(merchant.getOwnerUserId());
    }

    @Override
    @Transactional
    public void disable(Long merchantId) {
        if (Merchant.PLATFORM_MERCHANT_ID == merchantId) {
            throw new BizException(ResultCode.BAD_REQUEST, "平台自营店铺不能禁用");
        }
        Merchant merchant = requireMerchant(merchantId);
        if (merchantMapper.disableIfActive(merchantId) == 0) {
            throw new BizException(ResultCode.BAD_REQUEST, "只有营业中的店铺可以禁用");
        }
        authIdentityService.evict(merchant.getOwnerUserId());
    }

    @Override
    @Transactional
    public void enable(Long merchantId) {
        if (Merchant.PLATFORM_MERCHANT_ID == merchantId) {
            throw new BizException(ResultCode.BAD_REQUEST, "平台自营店铺不能操作");
        }
        Merchant merchant = requireMerchant(merchantId);
        if (merchantMapper.enableIfDisabled(merchantId) == 0) {
            throw new BizException(ResultCode.BAD_REQUEST, "只有已禁用的店铺可以启用");
        }
        authIdentityService.evict(merchant.getOwnerUserId());
    }

    @Override
    public Merchant requireActive(Long merchantId) {
        Merchant merchant = merchantId == null ? null : merchantMapper.selectById(merchantId);
        if (merchant == null || !Merchant.ACTIVE.equals(merchant.getStatus())) {
            throw new BizException(ResultCode.NOT_FOUND, "店铺不存在或已停用");
        }
        return merchant;
    }

    private Merchant requireMerchant(Long merchantId) {
        Merchant merchant = merchantMapper.selectById(merchantId);
        if (merchant == null) {
            throw new BizException(ResultCode.NOT_FOUND, "店铺不存在");
        }
        return merchant;
    }

    private void applyFields(Merchant merchant, MerchantRequest request) {
        merchant.setName(request.name().trim());
        merchant.setLogo(request.logo() == null ? "" : request.logo());
        merchant.setDescription(request.description() == null ? "" : request.description());
        merchant.setContactName(request.contactName().trim());
        merchant.setContactPhone(request.contactPhone().trim());
    }

    private String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private MerchantView toView(Merchant merchant) {
        return toView(merchant, true);
    }

    private MerchantView toView(Merchant merchant, boolean includeShipAddress) {
        return new MerchantView(merchant.getId(), merchant.getName(), merchant.getLogo(),
                merchant.getDescription(), merchant.getContactName(), merchant.getContactPhone(),
                includeShipAddress ? merchant.getShipProvince() : "",
                includeShipAddress ? merchant.getShipCity() : "",
                includeShipAddress ? merchant.getShipDistrict() : "",
                includeShipAddress ? merchant.getShipAddress() : "",
                includeShipAddress ? merchant.getShipLng() : null,
                includeShipAddress ? merchant.getShipLat() : null,
                merchant.getStatus(), merchant.getRejectReason(), merchant.getOwnerUserId(),
                merchant.getCreatedAt());
    }

    private AdminMerchantView toAdminView(Merchant merchant, User owner) {
        return new AdminMerchantView(merchant.getId(), merchant.getName(), merchant.getLogo(),
                merchant.getDescription(), merchant.getContactName(), merchant.getContactPhone(),
                merchant.getShipProvince(), merchant.getShipCity(), merchant.getShipDistrict(),
                merchant.getShipAddress(), merchant.getShipLng(), merchant.getShipLat(),
                merchant.getStatus(), merchant.getRejectReason(), merchant.getOwnerUserId(),
                owner == null ? "" : owner.getUsername(), merchant.getCreatedAt());
    }

    private int normalizePage(int page) {
        return Math.max(page, 1);
    }

    private int normalizeSize(int size) {
        return Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
    }
}

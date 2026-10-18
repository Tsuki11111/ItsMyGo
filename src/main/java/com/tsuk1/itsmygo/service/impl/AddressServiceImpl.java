package com.tsuk1.itsmygo.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.tsuk1.itsmygo.common.ResultCode;
import com.tsuk1.itsmygo.common.exception.BizException;
import com.tsuk1.itsmygo.dto.AddressRequest;
import com.tsuk1.itsmygo.entity.Address;
import com.tsuk1.itsmygo.mapper.AddressMapper;
import com.tsuk1.itsmygo.service.AddressService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AddressServiceImpl implements AddressService {

    private final AddressMapper addressMapper;

    public AddressServiceImpl(AddressMapper addressMapper) {
        this.addressMapper = addressMapper;
    }

    @Override
    public List<Address> list(Long userId) {
        return addressMapper.selectList(Wrappers.<Address>lambdaQuery()
                .eq(Address::getUserId, userId)
                .orderByDesc(Address::getIsDefault)
                .orderByDesc(Address::getId));
    }

    @Override
    @Transactional
    public Address create(Long userId, AddressRequest request) {
        Address address = new Address();
        apply(address, request);
        address.setUserId(userId);
        if (isDefault(address)) {
            clearDefault(userId);
        }
        addressMapper.insert(address);
        return address;
    }

    @Override
    @Transactional
    public Address update(Long userId, Long id, AddressRequest request) {
        Address address = requireOwned(userId, id);
        apply(address, request);
        if (isDefault(address)) {
            clearDefault(userId);
        }
        addressMapper.updateById(address);
        return address;
    }

    @Override
    public void delete(Long userId, Long id) {
        requireOwned(userId, id);
        addressMapper.deleteById(id);
    }

    @Override
    @Transactional
    public void setDefault(Long userId, Long id) {
        requireOwned(userId, id);
        clearDefault(userId);

        Address update = new Address();
        update.setId(id);
        update.setIsDefault(1);
        addressMapper.updateById(update);
    }

    private void apply(Address address, AddressRequest request) {
        address.setReceiver(request.receiver());
        address.setPhone(request.phone());
        address.setProvince(request.province());
        address.setCity(request.city());
        address.setDistrict(request.district());
        address.setDetail(request.detail());
        address.setIsDefault(Boolean.TRUE.equals(request.isDefault()) ? 1 : 0);
    }

    private boolean isDefault(Address address) {
        return Integer.valueOf(1).equals(address.getIsDefault());
    }

    /** 一个用户至多一个默认地址 */
    private void clearDefault(Long userId) {
        Address reset = new Address();
        reset.setIsDefault(0);
        addressMapper.update(reset, Wrappers.<Address>lambdaUpdate()
                .eq(Address::getUserId, userId)
                .eq(Address::getIsDefault, 1));
    }

    /**
     * 越权校验的唯一出口：别人的地址一律按「不存在」处理，
     * 不返回 403 —— 那等于告诉调用方「这条数据存在，只是不属于你」。
     */
    private Address requireOwned(Long userId, Long id) {
        Address address = addressMapper.selectById(id);
        if (address == null || !userId.equals(address.getUserId())) {
            throw new BizException(ResultCode.NOT_FOUND, "地址不存在");
        }
        return address;
    }
}

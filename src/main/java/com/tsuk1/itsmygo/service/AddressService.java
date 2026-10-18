package com.tsuk1.itsmygo.service;

import com.tsuk1.itsmygo.dto.AddressRequest;
import com.tsuk1.itsmygo.entity.Address;

import java.util.List;

public interface AddressService {

    List<Address> list(Long userId);

    Address create(Long userId, AddressRequest request);

    Address update(Long userId, Long id, AddressRequest request);

    void delete(Long userId, Long id);

    /**
     * 设为默认地址（幂等）。
     * 单独开一个接口而不是让客户端回传全量 PUT —— 后者会拿旧快照覆盖掉这期间改过的字段。
     */
    void setDefault(Long userId, Long id);
}

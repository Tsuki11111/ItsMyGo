package com.tsuk1.itsmygo.service;

import com.tsuk1.itsmygo.dto.AddressRequest;
import com.tsuk1.itsmygo.entity.Address;

import java.util.List;

public interface AddressService {

    List<Address> list(Long userId);

    Address create(Long userId, AddressRequest request);

    Address update(Long userId, Long id, AddressRequest request);

    void delete(Long userId, Long id);
}

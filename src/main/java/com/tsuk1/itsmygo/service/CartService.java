package com.tsuk1.itsmygo.service;

import com.tsuk1.itsmygo.dto.CartAddRequest;
import com.tsuk1.itsmygo.dto.CartUpdateRequest;
import com.tsuk1.itsmygo.dto.CartView;

public interface CartService {

    CartView list(Long userId);

    CartView add(Long userId, CartAddRequest request);

    CartView update(Long userId, Long itemId, CartUpdateRequest request);

    CartView remove(Long userId, Long itemId);
}

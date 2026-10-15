package com.tsuk1.itsmygo.controller;

import com.tsuk1.itsmygo.common.Result;
import com.tsuk1.itsmygo.dto.CartAddRequest;
import com.tsuk1.itsmygo.dto.CartUpdateRequest;
import com.tsuk1.itsmygo.dto.CartView;
import com.tsuk1.itsmygo.security.CurrentUser;
import com.tsuk1.itsmygo.service.CartService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 购物车：全部需登录，且只操作自己的数据 */
@RestController
@RequestMapping("/api/cart")
public class CartController {

    private final CartService cartService;

    public CartController(CartService cartService) {
        this.cartService = cartService;
    }

    @GetMapping
    public Result<CartView> list(@AuthenticationPrincipal CurrentUser user) {
        return Result.ok(cartService.list(user.id()));
    }

    @PostMapping("/items")
    public Result<CartView> add(@AuthenticationPrincipal CurrentUser user,
                                @Valid @RequestBody CartAddRequest request) {
        return Result.ok(cartService.add(user.id(), request));
    }

    @PutMapping("/items/{id}")
    public Result<CartView> update(@AuthenticationPrincipal CurrentUser user,
                                   @PathVariable Long id,
                                   @Valid @RequestBody CartUpdateRequest request) {
        return Result.ok(cartService.update(user.id(), id, request));
    }

    @DeleteMapping("/items/{id}")
    public Result<CartView> remove(@AuthenticationPrincipal CurrentUser user, @PathVariable Long id) {
        return Result.ok(cartService.remove(user.id(), id));
    }
}

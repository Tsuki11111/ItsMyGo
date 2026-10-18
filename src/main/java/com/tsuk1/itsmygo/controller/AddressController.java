package com.tsuk1.itsmygo.controller;

import com.tsuk1.itsmygo.common.Result;
import com.tsuk1.itsmygo.dto.AddressRequest;
import com.tsuk1.itsmygo.entity.Address;
import com.tsuk1.itsmygo.security.CurrentUser;
import com.tsuk1.itsmygo.service.AddressService;
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

import java.util.List;

@RestController
@RequestMapping("/api/addresses")
public class AddressController {

    private final AddressService addressService;

    public AddressController(AddressService addressService) {
        this.addressService = addressService;
    }

    @GetMapping
    public Result<List<Address>> list(@AuthenticationPrincipal CurrentUser user) {
        return Result.ok(addressService.list(user.id()));
    }

    @PostMapping
    public Result<Address> create(@AuthenticationPrincipal CurrentUser user,
                                  @Valid @RequestBody AddressRequest request) {
        return Result.ok(addressService.create(user.id(), request));
    }

    @PutMapping("/{id}")
    public Result<Address> update(@AuthenticationPrincipal CurrentUser user,
                                  @PathVariable Long id,
                                  @Valid @RequestBody AddressRequest request) {
        return Result.ok(addressService.update(user.id(), id, request));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@AuthenticationPrincipal CurrentUser user, @PathVariable Long id) {
        addressService.delete(user.id(), id);
        return Result.ok();
    }

    /** 设为默认地址。幂等；同一个用户至多一个默认地址 */
    @PutMapping("/{id}/default")
    public Result<Void> setDefault(@AuthenticationPrincipal CurrentUser user, @PathVariable Long id) {
        addressService.setDefault(user.id(), id);
        return Result.ok();
    }
}

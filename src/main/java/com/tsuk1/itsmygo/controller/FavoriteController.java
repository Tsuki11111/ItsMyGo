package com.tsuk1.itsmygo.controller;

import com.tsuk1.itsmygo.common.Result;
import com.tsuk1.itsmygo.dto.FavoriteView;
import com.tsuk1.itsmygo.dto.PageResponse;
import com.tsuk1.itsmygo.security.CurrentUser;
import com.tsuk1.itsmygo.service.FavoriteService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 顾客端：商品收藏，只操作自己的数据 */
@RestController
@RequestMapping("/api/favorites")
public class FavoriteController {

    private final FavoriteService favoriteService;

    public FavoriteController(FavoriteService favoriteService) {
        this.favoriteService = favoriteService;
    }

    @GetMapping
    public Result<PageResponse<FavoriteView>> page(@AuthenticationPrincipal CurrentUser user,
                                                   @RequestParam(defaultValue = "1") int page,
                                                   @RequestParam(defaultValue = "12") int size) {
        return Result.ok(favoriteService.page(user.id(), page, size));
    }

    /** 商品详情页回显收藏状态 */
    @GetMapping("/{productId}/status")
    public Result<Boolean> status(@AuthenticationPrincipal CurrentUser user,
                                  @PathVariable Long productId) {
        return Result.ok(favoriteService.isFavorited(user.id(), productId));
    }

    /** 幂等收藏 */
    @PostMapping("/{productId}")
    public Result<Void> add(@AuthenticationPrincipal CurrentUser user, @PathVariable Long productId) {
        favoriteService.add(user.id(), productId);
        return Result.ok();
    }

    /** 幂等取消 */
    @DeleteMapping("/{productId}")
    public Result<Void> remove(@AuthenticationPrincipal CurrentUser user, @PathVariable Long productId) {
        favoriteService.remove(user.id(), productId);
        return Result.ok();
    }
}

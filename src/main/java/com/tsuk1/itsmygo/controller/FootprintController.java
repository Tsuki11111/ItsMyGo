package com.tsuk1.itsmygo.controller;

import com.tsuk1.itsmygo.common.Result;
import com.tsuk1.itsmygo.dto.FootprintView;
import com.tsuk1.itsmygo.dto.PageResponse;
import com.tsuk1.itsmygo.security.CurrentUser;
import com.tsuk1.itsmygo.service.FootprintService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 顾客端：浏览足迹，只操作自己的数据 */
@RestController
@RequestMapping("/api/footprints")
public class FootprintController {

    private final FootprintService footprintService;

    public FootprintController(FootprintService footprintService) {
        this.footprintService = footprintService;
    }

    /** 商品详情页加载成功后静默上报；同一商品只更新时间 */
    @PostMapping("/{productId}")
    public Result<Void> record(@AuthenticationPrincipal CurrentUser user, @PathVariable Long productId) {
        footprintService.record(user.id(), productId);
        return Result.ok();
    }

    @GetMapping
    public Result<PageResponse<FootprintView>> page(@AuthenticationPrincipal CurrentUser user,
                                                    @RequestParam(defaultValue = "1") int page,
                                                    @RequestParam(defaultValue = "12") int size) {
        return Result.ok(footprintService.page(user.id(), page, size));
    }

    @DeleteMapping
    public Result<Void> clear(@AuthenticationPrincipal CurrentUser user) {
        footprintService.clear(user.id());
        return Result.ok();
    }
}

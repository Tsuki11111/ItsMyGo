package com.tsuk1.itsmygo.controller;

import com.tsuk1.itsmygo.common.Result;
import com.tsuk1.itsmygo.dto.ActivityPublicView;
import com.tsuk1.itsmygo.service.ActivityService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 顾客端活动中心：活动列表与详情，均可匿名访问 */
@RestController
@RequestMapping("/api/activities")
public class ActivityController {

    private final ActivityService activityService;

    public ActivityController(ActivityService activityService) {
        this.activityService = activityService;
    }

    @GetMapping
    public Result<List<ActivityPublicView>> list() {
        return Result.ok(activityService.list());
    }

    @GetMapping("/{id}")
    public Result<ActivityPublicView> detail(@PathVariable Long id) {
        return Result.ok(activityService.detail(id));
    }
}

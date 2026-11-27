package com.tsuk1.itsmygo.service;

import com.tsuk1.itsmygo.dto.ActivityPublicView;

import java.util.List;

/** 顾客端活动中心 */
public interface ActivityService {

    List<ActivityPublicView> list();

    ActivityPublicView detail(Long activityId);
}

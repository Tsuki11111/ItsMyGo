package com.tsuk1.itsmygo.service;

import com.tsuk1.itsmygo.dto.ActivityRequest;
import com.tsuk1.itsmygo.dto.ActivityUpdateRequest;
import com.tsuk1.itsmygo.dto.ActivityView;

import java.util.List;

/** 管理端活动 CRUD；类型专属配置（抽奖奖项）由对应 Service 负责 */
public interface ActivityAdminService {

    List<ActivityView> list();

    ActivityView create(ActivityRequest request);

    ActivityView update(Long activityId, ActivityUpdateRequest request);

    ActivityView updateStatus(Long activityId, int status);
}

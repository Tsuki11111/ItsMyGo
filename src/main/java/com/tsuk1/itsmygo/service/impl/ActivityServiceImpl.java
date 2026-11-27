package com.tsuk1.itsmygo.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.tsuk1.itsmygo.common.ResultCode;
import com.tsuk1.itsmygo.common.exception.BizException;
import com.tsuk1.itsmygo.dto.ActivityPublicView;
import com.tsuk1.itsmygo.entity.Activity;
import com.tsuk1.itsmygo.entity.ActivityStatus;
import com.tsuk1.itsmygo.entity.ActivityType;
import com.tsuk1.itsmygo.mapper.ActivityMapper;
import com.tsuk1.itsmygo.service.ActivityService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class ActivityServiceImpl implements ActivityService {

    private final ActivityMapper activityMapper;

    public ActivityServiceImpl(ActivityMapper activityMapper) {
        this.activityMapper = activityMapper;
    }

    @Override
    public List<ActivityPublicView> list() {
        List<Activity> activities = activityMapper.selectList(Wrappers.<Activity>lambdaQuery()
                .orderByDesc(Activity::getStartAt)
                .orderByDesc(Activity::getId));
        List<ActivityPublicView> views = new ArrayList<>(activities.size());
        for (Activity activity : activities) {
            views.add(toView(activity));
        }
        return views;
    }

    @Override
    public ActivityPublicView detail(Long activityId) {
        Activity activity = activityMapper.selectById(activityId);
        if (activity == null) {
            throw new BizException(ResultCode.NOT_FOUND, "活动不存在");
        }
        return toView(activity);
    }

    private ActivityPublicView toView(Activity activity) {
        ActivityStatus status = ActivityStatus.fromCode(activity.getStatus());
        return new ActivityPublicView(
                activity.getId(),
                activity.getType(),
                ActivityType.labelOf(activity.getType()),
                activity.getName(),
                activity.getDescription(),
                activity.getPrimaryColor(),
                activity.getCoverImage(),
                activity.getStatus(),
                status.label(),
                activity.getStartAt(),
                activity.getEndAt());
    }
}

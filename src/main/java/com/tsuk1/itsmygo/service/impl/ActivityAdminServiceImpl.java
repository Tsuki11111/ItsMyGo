package com.tsuk1.itsmygo.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.tsuk1.itsmygo.common.ResultCode;
import com.tsuk1.itsmygo.common.exception.BizException;
import com.tsuk1.itsmygo.dto.ActivityRequest;
import com.tsuk1.itsmygo.dto.ActivityUpdateRequest;
import com.tsuk1.itsmygo.dto.ActivityView;
import com.tsuk1.itsmygo.entity.Activity;
import com.tsuk1.itsmygo.entity.ActivityStatus;
import com.tsuk1.itsmygo.entity.ActivityType;
import com.tsuk1.itsmygo.mapper.ActivityMapper;
import com.tsuk1.itsmygo.service.ActivityAdminService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class ActivityAdminServiceImpl implements ActivityAdminService {

    private final ActivityMapper activityMapper;

    public ActivityAdminServiceImpl(ActivityMapper activityMapper) {
        this.activityMapper = activityMapper;
    }

    @Override
    public List<ActivityView> list() {
        List<Activity> activities = activityMapper.selectList(Wrappers.<Activity>lambdaQuery()
                .orderByDesc(Activity::getId));
        List<ActivityView> views = new ArrayList<>(activities.size());
        for (Activity activity : activities) {
            views.add(toView(activity));
        }
        return views;
    }

    @Override
    public ActivityView create(ActivityRequest request) {
        ActivityType type = ActivityType.fromCode(request.type());
        if (type == null) {
            throw new BizException(ResultCode.BAD_REQUEST, "不支持的活动类型");
        }
        validateTime(request.startAt(), request.endAt());

        Activity activity = new Activity();
        activity.setType(type.name());
        activity.setName(request.name());
        activity.setDescription(request.description() == null ? "" : request.description());
        activity.setPrimaryColor(request.primaryColor());
        activity.setCoverImage(request.coverImage() == null ? "" : request.coverImage());
        activity.setStatus(ActivityStatus.ACTIVE.code());
        activity.setStartAt(request.startAt());
        activity.setEndAt(request.endAt());
        activityMapper.insert(activity);

        Activity saved = activityMapper.selectById(activity.getId());
        return toView(saved);
    }

    @Override
    public ActivityView update(Long activityId, ActivityUpdateRequest request) {
        requireActivity(activityId);
        validateTime(request.startAt(), request.endAt());

        activityMapper.update(null, Wrappers.<Activity>lambdaUpdate()
                .eq(Activity::getId, activityId)
                .set(Activity::getName, request.name())
                .set(Activity::getDescription, request.description() == null ? "" : request.description())
                .set(Activity::getPrimaryColor, request.primaryColor())
                .set(Activity::getCoverImage, request.coverImage() == null ? "" : request.coverImage())
                .set(Activity::getStartAt, request.startAt())
                .set(Activity::getEndAt, request.endAt()));

        return toView(activityMapper.selectById(activityId));
    }

    @Override
    public ActivityView updateStatus(Long activityId, int status) {
        requireActivity(activityId);
        ActivityStatus target = ActivityStatus.fromCode(status);
        activityMapper.update(null, Wrappers.<Activity>lambdaUpdate()
                .eq(Activity::getId, activityId)
                .set(Activity::getStatus, target.code()));
        return toView(activityMapper.selectById(activityId));
    }

    private Activity requireActivity(Long activityId) {
        Activity activity = activityMapper.selectById(activityId);
        if (activity == null) {
            throw new BizException(ResultCode.NOT_FOUND, "活动不存在");
        }
        return activity;
    }

    private void validateTime(java.time.LocalDateTime startAt, java.time.LocalDateTime endAt) {
        if (!startAt.isBefore(endAt)) {
            throw new BizException(ResultCode.BAD_REQUEST, "结束时间必须晚于开始时间");
        }
    }

    private ActivityView toView(Activity activity) {
        ActivityStatus status = ActivityStatus.fromCode(activity.getStatus());
        return new ActivityView(
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
                activity.getEndAt(),
                activity.getCreatedAt());
    }
}

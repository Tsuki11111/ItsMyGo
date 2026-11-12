package com.tsuk1.itsmygo.service;

import com.tsuk1.itsmygo.dto.profile.UserProfileView;

/** 用户画像：规则统计 + 短时缓存，导购与智能推广共用。 */
public interface UserProfileService {

    UserProfileView profile(long userId);
}

package com.tsuk1.itsmygo.service.impl;

import com.tsuk1.itsmygo.dto.agent.AgentPreferenceRequest;
import com.tsuk1.itsmygo.dto.agent.AgentPreferenceView;
import com.tsuk1.itsmygo.entity.AgentPreference;
import com.tsuk1.itsmygo.mapper.AgentPreferenceMapper;
import com.tsuk1.itsmygo.service.AgentPreferenceService;
import org.springframework.stereotype.Service;

@Service
public class AgentPreferenceServiceImpl implements AgentPreferenceService {

    // 与 V33 建表默认值保持一致：推广关（用户主动开启）、联网开、流式关
    private static final boolean DEFAULT_PROMOTION_ENABLED = false;
    private static final boolean DEFAULT_WEB_SEARCH_ENABLED = true;
    private static final boolean DEFAULT_STREAM_ENABLED = false;

    private final AgentPreferenceMapper preferenceMapper;

    public AgentPreferenceServiceImpl(AgentPreferenceMapper preferenceMapper) {
        this.preferenceMapper = preferenceMapper;
    }

    @Override
    public AgentPreferenceView get(long userId) {
        AgentPreference stored = preferenceMapper.selectById(userId);
        if (stored == null) {
            return new AgentPreferenceView(DEFAULT_PROMOTION_ENABLED,
                    DEFAULT_WEB_SEARCH_ENABLED, DEFAULT_STREAM_ENABLED);
        }
        return new AgentPreferenceView(
                Boolean.TRUE.equals(stored.getPromotionEnabled()),
                Boolean.TRUE.equals(stored.getWebSearchEnabled()),
                Boolean.TRUE.equals(stored.getStreamEnabled()));
    }

    @Override
    public AgentPreferenceView save(long userId, AgentPreferenceRequest request) {
        // 三个字段都不为 null（Controller 用 @NotNull 校过），直接全量覆盖
        preferenceMapper.upsert(userId, request.promotionEnabled(),
                request.webSearchEnabled(), request.streamEnabled());
        return new AgentPreferenceView(request.promotionEnabled(),
                request.webSearchEnabled(), request.streamEnabled());
    }
}

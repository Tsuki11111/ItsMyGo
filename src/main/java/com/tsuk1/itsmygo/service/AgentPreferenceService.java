package com.tsuk1.itsmygo.service;

import com.tsuk1.itsmygo.dto.agent.AgentPreferenceRequest;
import com.tsuk1.itsmygo.dto.agent.AgentPreferenceView;

/** 顾客的客服设置：读取（无行给默认值）/ 保存（upsert）。 */
public interface AgentPreferenceService {

    /** 推广关、联网开、流式关；没有行时返回默认值，**不落库** */
    AgentPreferenceView get(long userId);

    /** 全量覆盖三个开关，返回保存后的值 */
    AgentPreferenceView save(long userId, AgentPreferenceRequest request);
}

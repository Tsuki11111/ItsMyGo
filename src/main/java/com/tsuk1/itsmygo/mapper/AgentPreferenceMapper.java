package com.tsuk1.itsmygo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tsuk1.itsmygo.entity.AgentPreference;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;

public interface AgentPreferenceMapper extends BaseMapper<AgentPreference> {

    /**
     * 一条 SQL 完成 upsert：并发保存同一用户时不会先查后插产生主键冲突，
     * 也**不会**像 REPLACE 那样把后续「智能推广」要用的 last_promotion_date 清掉。
     */
    @Insert("""
            INSERT INTO user_agent_preferences
                (user_id, promotion_enabled, web_search_enabled, stream_enabled)
            VALUES (#{userId}, #{promotionEnabled}, #{webSearchEnabled}, #{streamEnabled})
            ON DUPLICATE KEY UPDATE
                promotion_enabled = VALUES(promotion_enabled),
                web_search_enabled = VALUES(web_search_enabled),
                stream_enabled = VALUES(stream_enabled)
            """)
    int upsert(@Param("userId") Long userId,
               @Param("promotionEnabled") Boolean promotionEnabled,
               @Param("webSearchEnabled") Boolean webSearchEnabled,
               @Param("streamEnabled") Boolean streamEnabled);
}

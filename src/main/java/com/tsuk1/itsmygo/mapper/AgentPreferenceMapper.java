package com.tsuk1.itsmygo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tsuk1.itsmygo.entity.AgentPreference;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDate;

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

    /** 每日推广批量的用户：开了推广且账号启用，按用户 id 顺序取一批 */
    @Select("""
            SELECT p.user_id
            FROM user_agent_preferences p
            JOIN users u ON u.id = p.user_id
            WHERE p.promotion_enabled = 1 AND u.status = 1
            ORDER BY p.user_id
            LIMIT #{limit}
            """)
    java.util.List<Long> selectPromotionUserIds(@Param("limit") int limit);

    /** 发送成功后标记今天已推；每天最多 1 条的最终落点 */
    @Update("""
            UPDATE user_agent_preferences
            SET last_promotion_date = #{date}
            WHERE user_id = #{userId}
            """)
    int markPromotionSent(@Param("userId") Long userId, @Param("date") LocalDate date);
}

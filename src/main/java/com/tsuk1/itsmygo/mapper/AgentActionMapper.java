package com.tsuk1.itsmygo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tsuk1.itsmygo.entity.AgentAction;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

public interface AgentActionMapper extends BaseMapper<AgentAction> {

    /** 条件更新：只有 PENDING_CONFIRM 才能被确认/取消，天然防双击 */
    @Update("UPDATE agent_actions SET status = #{to}, updated_at = NOW() "
            + "WHERE action_no = #{actionNo} AND user_id = #{userId} AND status = #{from}")
    int casStatus(@Param("actionNo") String actionNo,
                  @Param("userId") Long userId,
                  @Param("from") String from,
                  @Param("to") String to);

    /** 进入执行态并记录确认时间；与 casStatus 分开，避免取消/过期也写 confirmed_at */
    @Update("UPDATE agent_actions SET status = 'EXECUTING', confirmed_at = NOW(), updated_at = NOW() "
            + "WHERE action_no = #{actionNo} AND user_id = #{userId} AND status = 'PENDING_CONFIRM'")
    int startExecution(@Param("actionNo") String actionNo, @Param("userId") Long userId);
}

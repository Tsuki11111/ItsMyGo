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

    /** agent 工具执行成功：只有 EXECUTING 能落结果，防重复执行 */
    @Update("UPDATE agent_actions SET status = 'SUCCEEDED', result_json = #{resultJson}, "
            + "error_message = '', executed_at = NOW(), updated_at = NOW() "
            + "WHERE action_no = #{actionNo} AND user_id = #{userId} AND status = 'EXECUTING'")
    int finishExecution(@Param("actionNo") String actionNo,
                        @Param("userId") Long userId,
                        @Param("resultJson") String resultJson);

    /** agent 工具执行失败：只有 EXECUTING 能落失败，避免覆盖已完成动作 */
    @Update("UPDATE agent_actions SET status = 'FAILED', error_message = #{errorMessage}, "
            + "executed_at = NOW(), updated_at = NOW() "
            + "WHERE action_no = #{actionNo} AND user_id = #{userId} AND status = 'EXECUTING'")
    int failExecution(@Param("actionNo") String actionNo,
                      @Param("userId") Long userId,
                      @Param("errorMessage") String errorMessage);
}

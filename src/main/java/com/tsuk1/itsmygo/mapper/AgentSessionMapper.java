package com.tsuk1.itsmygo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tsuk1.itsmygo.entity.AgentSession;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

public interface AgentSessionMapper extends BaseMapper<AgentSession> {

    @Insert("""
            INSERT INTO agent_sessions (user_id, session_id)
            VALUES (#{userId}, #{sessionId})
            """)
    int insertSession(@Param("userId") Long userId, @Param("sessionId") String sessionId);

    @Select("""
            SELECT * FROM agent_sessions
            WHERE session_id = #{sessionId} AND user_id = #{userId}
            """)
    AgentSession selectOwned(@Param("sessionId") String sessionId, @Param("userId") Long userId);

    @Update("""
            UPDATE agent_sessions SET last_active_at = NOW()
            WHERE session_id = #{sessionId} AND user_id = #{userId}
            """)
    int touch(@Param("sessionId") String sessionId, @Param("userId") Long userId);
}

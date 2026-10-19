package com.tsuk1.itsmygo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tsuk1.itsmygo.entity.ChatMessage;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface ChatMessageMapper extends BaseMapper<ChatMessage> {

    /** 分页取消息：beforeId 向上翻历史，afterId 断线补齐；返回 id 倒序，服务层按需反转 */
    @Select("""
            <script>
            SELECT * FROM messages
            WHERE conversation_id = #{conversationId}
            <if test="beforeId != null"> AND id &lt; #{beforeId}</if>
            <if test="afterId != null"> AND id &gt; #{afterId}</if>
            ORDER BY id DESC
            LIMIT #{limit}
            </script>
            """)
    List<ChatMessage> selectRecent(@Param("conversationId") Long conversationId,
                                   @Param("beforeId") Long beforeId,
                                   @Param("afterId") Long afterId,
                                   @Param("limit") int limit);
}

package com.tsuk1.itsmygo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tsuk1.itsmygo.entity.Conversation;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

public interface ConversationMapper extends BaseMapper<Conversation> {

    /**
     * 并发安全地找或建会话：唯一键冲突时静默忽略，调用方回查。
     * 空会话（last_message_id 为 NULL）不进入会话列表。
     */
    @Insert("""
            INSERT IGNORE INTO conversations
                (customer_user_id, merchant_id, customer_unread, merchant_unread,
                 customer_last_read_message_id, merchant_last_read_message_id)
            VALUES (#{customerUserId}, #{merchantId}, 0, 0, 0, 0)
            """)
    int insertIgnore(@Param("customerUserId") Long customerUserId,
                     @Param("merchantId") Long merchantId);

    /** 只有更大的消息 id 才能覆盖「最后一条消息」，防止并发下旧消息倒灌 */
    @Update("""
            UPDATE conversations
            SET last_message_id = #{messageId},
                last_message_preview = #{preview},
                last_message_at = #{messageAt}
            WHERE id = #{id}
              AND (last_message_id IS NULL OR last_message_id < #{messageId})
            """)
    int updateLastMessage(@Param("id") Long id,
                          @Param("messageId") Long messageId,
                          @Param("preview") String preview,
                          @Param("messageAt") java.time.LocalDateTime messageAt);

    @Update("""
            UPDATE conversations
            SET customer_unread = customer_unread + #{customerInc},
                merchant_unread = merchant_unread + #{merchantInc}
            WHERE id = #{id}
            """)
    int addUnread(@Param("id") Long id,
                  @Param("customerInc") int customerInc,
                  @Param("merchantInc") int merchantInc);

    /** 已读指针只前进；未读随后用 COUNT 重算，避免与并发新消息竞争时丢计数 */
    @Update("""
            UPDATE conversations
            SET customer_last_read_message_id =
                GREATEST(customer_last_read_message_id, #{lastReadMessageId})
            WHERE id = #{id}
            """)
    int advanceCustomerRead(@Param("id") Long id,
                            @Param("lastReadMessageId") Long lastReadMessageId);

    @Update("""
            UPDATE conversations
            SET customer_unread = (
                SELECT COUNT(*) FROM messages m
                WHERE m.conversation_id = #{id}
                  AND m.id > conversations.customer_last_read_message_id
                  AND m.sender_type <> 'CUSTOMER'
            )
            WHERE id = #{id}
            """)
    int recountCustomerUnread(@Param("id") Long id);

    @Update("""
            UPDATE conversations
            SET merchant_last_read_message_id =
                GREATEST(merchant_last_read_message_id, #{lastReadMessageId})
            WHERE id = #{id}
            """)
    int advanceMerchantRead(@Param("id") Long id,
                            @Param("lastReadMessageId") Long lastReadMessageId);

    @Update("""
            UPDATE conversations
            SET merchant_unread = (
                SELECT COUNT(*) FROM messages m
                WHERE m.conversation_id = #{id}
                  AND m.id > conversations.merchant_last_read_message_id
                  AND m.sender_type <> 'MERCHANT'
            )
            WHERE id = #{id}
            """)
    int recountMerchantUnread(@Param("id") Long id);

    @Select("SELECT COALESCE(SUM(customer_unread), 0) FROM conversations WHERE customer_user_id = #{userId}")
    long sumCustomerUnread(@Param("userId") Long userId);

    @Select("SELECT COALESCE(SUM(merchant_unread), 0) FROM conversations WHERE merchant_id = #{merchantId}")
    long sumMerchantUnread(@Param("merchantId") Long merchantId);
}

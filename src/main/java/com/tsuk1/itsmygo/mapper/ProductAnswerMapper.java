package com.tsuk1.itsmygo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tsuk1.itsmygo.entity.ProductAnswer;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/** 商品回答 */
public interface ProductAnswerMapper extends BaseMapper<ProductAnswer> {

    /** 批量取每个提问最新 N 条回答，避免列表页 N+1 */
    @Select("""
            <script>
            SELECT id, question_id, user_id, content, status, created_at, updated_at
            FROM (
                SELECT pa.*,
                       ROW_NUMBER() OVER (PARTITION BY question_id ORDER BY id DESC) AS rn
                FROM product_answers pa
                WHERE pa.status = 1 AND pa.question_id IN
                <foreach collection="questionIds" item="id" open="(" separator="," close=")">#{id}</foreach>
            ) ranked
            WHERE rn &lt;= #{limit}
            ORDER BY question_id, id DESC
            </script>
            """)
    List<ProductAnswer> selectPreviewAnswers(@Param("questionIds") List<Long> questionIds,
                                             @Param("limit") int limit);
}

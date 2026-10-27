package com.tsuk1.itsmygo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tsuk1.itsmygo.entity.ProductQuestion;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/** 商品提问 */
public interface ProductQuestionMapper extends BaseMapper<ProductQuestion> {

    /** 回答数冗余字段：只对仍可见的提问递增，避免隐藏提问被继续顶高 */
    @Update("UPDATE product_questions SET answer_count = answer_count + 1 "
            + "WHERE id = #{questionId} AND status = 1")
    int incrementAnswerCount(@Param("questionId") Long questionId);
}

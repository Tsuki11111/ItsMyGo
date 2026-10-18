package com.tsuk1.itsmygo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tsuk1.itsmygo.entity.UserFavorite;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;

public interface UserFavoriteMapper extends BaseMapper<UserFavorite> {

    /** 幂等收藏：唯一键冲突时静默忽略，返回值只用于区分「新收藏 / 已收藏」 */
    @Insert("""
            INSERT IGNORE INTO user_favorites (user_id, product_id)
            VALUES (#{userId}, #{productId})
            """)
    int insertIgnore(@Param("userId") Long userId, @Param("productId") Long productId);
}

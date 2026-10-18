package com.tsuk1.itsmygo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tsuk1.itsmygo.entity.UserFootprint;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface UserFootprintMapper extends BaseMapper<UserFootprint> {

    /** 按商品去重：已有则只把 viewed_at 更新到当前时间 */
    @Insert("""
            INSERT INTO user_footprints (user_id, product_id, viewed_at)
            VALUES (#{userId}, #{productId}, NOW(3))
            ON DUPLICATE KEY UPDATE viewed_at = NOW(3)
            """)
    int upsert(@Param("userId") Long userId, @Param("productId") Long productId);

    @Select("SELECT COUNT(*) FROM user_footprints WHERE user_id = #{userId}")
    int countByUser(@Param("userId") Long userId);

    /**
     * 只保留最近 keep 条。
     *
     * ⚠️ MySQL 8 的两个限制逼出双层派生表：
     *   - `IN (SELECT ... LIMIT)` 不支持（错误 1235）
     *   - DELETE 的同表子查询不允许（错误 1093）
     * 外层再包一层派生表（keep_rows）后两个限制都绕开了。
     */
    @Delete("""
            DELETE FROM user_footprints
            WHERE user_id = #{userId}
              AND id NOT IN (
                SELECT id FROM (
                  SELECT id FROM user_footprints
                  WHERE user_id = #{userId}
                  ORDER BY viewed_at DESC, id DESC
                  LIMIT #{keep}
                ) keep_rows
              )
            """)
    int trimToLatest(@Param("userId") Long userId, @Param("keep") int keep);
}

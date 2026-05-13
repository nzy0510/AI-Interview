package com.interview.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.interview.entity.UserDailyUsage;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.util.List;

@Mapper
public interface UserDailyUsageMapper extends BaseMapper<UserDailyUsage> {

    @Insert("""
            INSERT INTO user_daily_usage
              (user_id, usage_date, quota_type, used_count, limit_count, create_time, update_time)
            VALUES
              (#{usage.userId}, #{usage.usageDate}, #{usage.quotaType}, #{usage.usedCount}, #{usage.limitCount}, NOW(), NOW())
            ON DUPLICATE KEY UPDATE
              used_count = VALUES(used_count),
              limit_count = VALUES(limit_count),
              update_time = NOW()
            """)
    void upsertUsage(@Param("usage") UserDailyUsage usage);

    @Select("""
            SELECT id, user_id, usage_date, quota_type, used_count, limit_count, create_time, update_time
            FROM user_daily_usage
            WHERE user_id = #{userId}
              AND usage_date = #{usageDate}
            ORDER BY quota_type ASC
            """)
    List<UserDailyUsage> selectUsageForDate(@Param("userId") Long userId, @Param("usageDate") LocalDate usageDate);

    @Select("""
            SELECT id, user_id, usage_date, quota_type, used_count, limit_count, create_time, update_time
            FROM user_daily_usage
            WHERE usage_date = #{usageDate}
            ORDER BY user_id ASC, quota_type ASC
            LIMIT #{limit}
            """)
    List<UserDailyUsage> selectUsageSnapshot(@Param("usageDate") LocalDate usageDate, @Param("limit") int limit);
}

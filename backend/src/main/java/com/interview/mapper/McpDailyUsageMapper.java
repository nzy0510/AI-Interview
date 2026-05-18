package com.interview.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.interview.entity.McpDailyUsage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.util.List;

@Mapper
public interface McpDailyUsageMapper extends BaseMapper<McpDailyUsage> {

    @Select("""
            SELECT id, user_id, usage_date, quota_type, used_count, limit_count, create_time, update_time
            FROM mcp_daily_usage
            WHERE user_id=#{userId} AND usage_date=#{usageDate}
            ORDER BY quota_type ASC
            """)
    List<McpDailyUsage> selectUsageForDate(@Param("userId") Long userId, @Param("usageDate") LocalDate usageDate);
}

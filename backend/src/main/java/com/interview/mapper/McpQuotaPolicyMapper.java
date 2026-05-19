package com.interview.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.interview.entity.McpQuotaPolicy;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface McpQuotaPolicyMapper extends BaseMapper<McpQuotaPolicy> {

    @Select("""
            SELECT id, role_name, quota_type, label, limit_count, display_order, create_time, update_time
            FROM mcp_quota_policy
            WHERE role_name=#{roleName}
            ORDER BY display_order ASC, quota_type ASC
            """)
    List<McpQuotaPolicy> selectByRole(@Param("roleName") String roleName);
}

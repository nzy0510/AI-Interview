package com.interview.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.interview.entity.McpAccessToken;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface McpAccessTokenMapper extends BaseMapper<McpAccessToken> {

    @Update("""
            UPDATE mcp_access_token
            SET status='REVOKED', revoked_at=NOW(), update_time=NOW()
            WHERE user_id=#{userId} AND status='ACTIVE'
            """)
    int revokeActiveByUserId(@Param("userId") Long userId);
}

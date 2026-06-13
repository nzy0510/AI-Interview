package com.interview.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.interview.entity.AppJob;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AppJobMapper extends BaseMapper<AppJob> {
}

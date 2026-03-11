package com.interview.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.interview.entity.InterviewRecord;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface InterviewRecordMapper extends BaseMapper<InterviewRecord> {
}

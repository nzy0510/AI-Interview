package com.interview.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.interview.entity.AppEventLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface AppEventLogMapper extends BaseMapper<AppEventLog> {

    @Select("""
            SELECT
              COALESCE(SUM(CASE WHEN event_type = 'PAGE_VIEW' THEN 1 ELSE 0 END), 0) AS pageViews,
              COUNT(DISTINCT CASE WHEN event_type = 'PAGE_VIEW'
                THEN COALESCE(CONCAT('u:', user_id), CONCAT('a:', anonymous_id), CONCAT('ip:', ip_hash))
                ELSE NULL END) AS uniqueVisitors,
              COALESCE(SUM(CASE WHEN event_type = 'REGISTER' AND success = 1 THEN 1 ELSE 0 END), 0) AS registrations,
              COALESCE(SUM(CASE WHEN event_type = 'LOGIN' AND success = 1 THEN 1 ELSE 0 END), 0) AS logins,
              COALESCE(SUM(CASE WHEN event_type = 'INTERVIEW_START' AND success = 1 THEN 1 ELSE 0 END), 0) AS interviewStarts,
              COALESCE(SUM(CASE WHEN event_type = 'INTERVIEW_FINISH' AND success = 1 THEN 1 ELSE 0 END), 0) AS interviewFinishes,
              COALESCE(SUM(CASE WHEN event_type = 'RESUME_PARSE' AND success = 1 THEN 1 ELSE 0 END), 0) AS resumeParses,
              COALESCE(SUM(CASE WHEN event_type IN ('MENTOR_REFRESH', 'MENTOR_GENERATE') AND success = 1 THEN 1 ELSE 0 END), 0) AS mentorGenerations,
              COALESCE(SUM(CASE WHEN event_type = 'FEEDBACK_SUBMIT' AND success = 1 THEN 1 ELSE 0 END), 0) AS feedbackCount,
              COALESCE(SUM(CASE WHEN success = 0 OR status_code >= 500 THEN 1 ELSE 0 END), 0) AS errorCount,
              COALESCE(SUM(CASE WHEN status_code = 429 THEN 1 ELSE 0 END), 0) AS limitedCount
            FROM app_event_log
            WHERE create_time >= DATE_SUB(NOW(), INTERVAL #{days} DAY)
            """)
    Map<String, Object> selectSummarySince(@Param("days") int days);

    @Select("""
            SELECT DATE_FORMAT(create_time, '%Y-%m-%d') AS day,
                   event_type AS eventType,
                   COUNT(*) AS count
            FROM app_event_log
            WHERE create_time >= DATE_SUB(NOW(), INTERVAL #{days} DAY)
            GROUP BY DATE_FORMAT(create_time, '%Y-%m-%d'), event_type
            ORDER BY day ASC, count DESC
            """)
    List<Map<String, Object>> selectDailyEventCounts(@Param("days") int days);

    @Select("""
            SELECT path AS path, COUNT(*) AS count
            FROM app_event_log
            WHERE create_time >= DATE_SUB(NOW(), INTERVAL #{days} DAY)
              AND path IS NOT NULL
            GROUP BY path
            ORDER BY count DESC
            LIMIT 20
            """)
    List<Map<String, Object>> selectTopPaths(@Param("days") int days);
}

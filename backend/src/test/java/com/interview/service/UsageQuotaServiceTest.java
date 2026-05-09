package com.interview.service;

import com.interview.exception.QuotaExceededException;
import com.interview.mapper.UserDailyUsageMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@DisplayName("UsageQuotaService — AI 成本额度")
@ExtendWith(MockitoExtension.class)
class UsageQuotaServiceTest {

    @Mock
    private UserDailyUsageMapper usageMapper;

    @Mock
    private DeveloperAccessService developerAccessService;

    @Test
    @DisplayName("超过每日面试次数时拒绝继续开始面试")
    void shouldRejectWhenDailyInterviewQuotaExceeded() {
        UsageQuotaService service = new UsageQuotaService(usageMapper, developerAccessService);
        ReflectionTestUtils.setField(service, "quotaEnabled", true);
        ReflectionTestUtils.setField(service, "interviewStartLimit", 2);

        service.consume(1L, UsageQuotaService.INTERVIEW_START);
        service.consume(1L, UsageQuotaService.INTERVIEW_START);

        assertThatThrownBy(() -> service.consume(1L, UsageQuotaService.INTERVIEW_START))
                .isInstanceOf(QuotaExceededException.class)
                .hasMessageContaining("今日可开始面试次数已用完");

        verify(usageMapper, times(3)).upsertUsage(any());
    }

    @Test
    @DisplayName("开发者白名单账号不消耗每日 AI 成本额度")
    void shouldSkipDailyQuotaForDeveloper() {
        UsageQuotaService service = new UsageQuotaService(usageMapper, developerAccessService);
        ReflectionTestUtils.setField(service, "quotaEnabled", true);
        ReflectionTestUtils.setField(service, "interviewStartLimit", 1);
        org.mockito.Mockito.when(developerAccessService.isDeveloper(1L)).thenReturn(true);

        service.consume(1L, UsageQuotaService.INTERVIEW_START);
        service.consume(1L, UsageQuotaService.INTERVIEW_START);

        verify(usageMapper, org.mockito.Mockito.never()).upsertUsage(any());
    }
}

package com.interview.service;

import com.interview.dto.McpTokenResponse;
import com.interview.dto.McpUsageResponse;
import com.interview.entity.McpAccessToken;
import com.interview.entity.McpDailyUsage;
import com.interview.entity.McpQuotaPolicy;
import com.interview.mapper.McpAccessTokenMapper;
import com.interview.mapper.McpDailyUsageMapper;
import com.interview.mapper.McpQuotaPolicyMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("McpTokenService — MCP 额度策略")
@ExtendWith(MockitoExtension.class)
class McpTokenServiceTest {

    @Mock
    private McpAccessTokenMapper tokenMapper;

    @Mock
    private McpDailyUsageMapper usageMapper;

    @Mock
    private McpQuotaPolicyMapper quotaPolicyMapper;

    @Mock
    private DeveloperAccessService developerAccessService;

    @Test
    @DisplayName("已有 token 时按 token role 读取 DB 额度策略")
    void shouldUseActiveTokenRoleForUsagePolicy() {
        McpAccessToken active = new McpAccessToken();
        active.setRole("DEVELOPER");
        when(tokenMapper.selectOne(any())).thenReturn(active);
        when(quotaPolicyMapper.selectByRole("DEVELOPER")).thenReturn(List.of(
                policy("DEVELOPER", "total", "每日 MCP 总调用", 77, 10),
                policy("DEVELOPER", "search", "每日题库检索", 33, 20)
        ));
        McpDailyUsage searchUsage = new McpDailyUsage();
        searchUsage.setQuotaType("search");
        searchUsage.setUsedCount(5);
        when(usageMapper.selectUsageForDate(eq(1L), any(LocalDate.class))).thenReturn(List.of(searchUsage));

        McpUsageResponse response = service().usage(1L);

        assertThat(response.getItems())
                .extracting(McpTokenResponse.QuotaItem::getType, McpTokenResponse.QuotaItem::getUsed,
                        McpTokenResponse.QuotaItem::getLimit, McpTokenResponse.QuotaItem::getRemaining)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("total", 0, 77, 77),
                        org.assertj.core.groups.Tuple.tuple("search", 5, 33, 28)
                );
        verify(developerAccessService, never()).isDeveloper(1L);
    }

    @Test
    @DisplayName("未生成 token 时按当前开发者身份展示将要获得的额度")
    void shouldShowExpectedDeveloperPolicyBeforeTokenGenerated() {
        when(tokenMapper.selectOne(any())).thenReturn(null);
        when(developerAccessService.isDeveloper(1L)).thenReturn(true);
        when(quotaPolicyMapper.selectByRole("DEVELOPER")).thenReturn(List.of(
                policy("DEVELOPER", "total", "每日 MCP 总调用", 77, 10)
        ));

        McpTokenResponse response = service().status(1L, "https://interwise.example.com");

        assertThat(response.isHasActiveToken()).isFalse();
        assertThat(response.getEndpointUrl()).isEqualTo("https://interwise.example.com/mcp");
        assertThat(response.getQuotas())
                .extracting(McpTokenResponse.QuotaItem::getType, McpTokenResponse.QuotaItem::getLimit)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("total", 77));
    }

    private McpTokenService service() {
        return new McpTokenService(tokenMapper, usageMapper, quotaPolicyMapper, developerAccessService);
    }

    private McpQuotaPolicy policy(String role, String type, String label, int limit, int order) {
        McpQuotaPolicy policy = new McpQuotaPolicy();
        policy.setRoleName(role);
        policy.setQuotaType(type);
        policy.setLabel(label);
        policy.setLimitCount(limit);
        policy.setDisplayOrder(order);
        return policy;
    }
}

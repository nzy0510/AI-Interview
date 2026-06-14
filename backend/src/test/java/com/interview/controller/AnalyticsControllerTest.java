package com.interview.controller;

import com.interview.service.AdminGuardService;
import com.interview.service.AnalyticsService;
import com.interview.service.AppEventService;
import com.interview.service.RequestUserResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@DisplayName("AnalyticsController — 运营统计路由")
class AnalyticsControllerTest {

    private MockMvc mockMvc;

    @Mock
    private AppEventService appEventService;

    @Mock
    private RequestUserResolver requestUserResolver;

    @Mock
    private AnalyticsService analyticsService;

    @Mock
    private AdminGuardService adminGuardService;

    @BeforeEach
    void setUp() {
        AnalyticsController controller = new AnalyticsController(
                appEventService,
                requestUserResolver,
                analyticsService,
                adminGuardService
        );
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    @DisplayName("用户侧每日额度查询旧路由已下线")
    void shouldNotExposeLegacyQuotaRoute() throws Exception {
        mockMvc.perform(get("/api/analytics/quota/me"))
                .andExpect(status().isNotFound());
    }
}

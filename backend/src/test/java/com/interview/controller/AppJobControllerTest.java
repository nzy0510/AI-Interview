package com.interview.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.interview.config.GlobalExceptionHandler;
import com.interview.entity.AppJob;
import com.interview.mapper.AppJobMapper;
import com.interview.service.AdminRoleService;
import com.interview.service.AppJobRecoveryService;
import com.interview.service.AppJobService;
import com.interview.service.RequestUserResolver;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@DisplayName("AppJobController — 作业轮询接口")
class AppJobControllerTest {

    private MockMvc mockMvc;

    @Mock
    private AppJobMapper appJobMapper;

    @Mock
    private AppJobService appJobService;

    @Mock
    private AppJobRecoveryService appJobRecoveryService;

    @Mock
    private RequestUserResolver requestUserResolver;

    @Mock
    private AdminRoleService adminRoleService;

    @BeforeEach
    void setUp() {
        AppJobController controller = new AppJobController(
                appJobMapper,
                appJobService,
                appJobRecoveryService,
                requestUserResolver,
                adminRoleService
        );
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("普通用户查询作业列表时查询自己的作业和公开作业")
    void shouldListVisibleJobsForOrdinaryUser() throws Exception {
        when(requestUserResolver.resolveUserId(any(HttpServletRequest.class))).thenReturn(11L);
        when(appJobMapper.selectList(any())).thenReturn(List.of(job(1L, 11L)));

        mockMvc.perform(get("/api/jobs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].id").value(1))
                .andExpect(jsonPath("$.data[0].ownerUserId").value(11))
                .andExpect(jsonPath("$.data[0].payloadJson").doesNotExist());

        ArgumentCaptor<QueryWrapper<AppJob>> captor = queryCaptor();
        verify(appJobMapper).selectList(captor.capture());
        assertThat(captor.getValue().getSqlSegment())
                .contains("owner_user_id")
                .contains("scope");
    }

    @Test
    @DisplayName("管理员查询作业列表时仍只返回自己的和公开作业")
    void shouldKeepAdminPrivateJobListScoped() throws Exception {
        when(requestUserResolver.resolveUserId(any(HttpServletRequest.class))).thenReturn(99L);
        AppJob publicJob = job(2L, 11L);
        publicJob.setScope("PUBLIC");
        when(appJobMapper.selectList(any())).thenReturn(List.of(publicJob));

        mockMvc.perform(get("/api/jobs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(2))
                .andExpect(jsonPath("$.data[0].ownerUserId").value(11));

        ArgumentCaptor<QueryWrapper<AppJob>> captor = queryCaptor();
        verify(appJobMapper).selectList(captor.capture());
        assertThat(captor.getValue().getSqlSegment())
                .contains("owner_user_id")
                .contains("scope");
    }

    @Test
    @DisplayName("管理员不能读取其他账号的私有作业详情")
    void shouldRejectAdminReadingOtherUsersPrivateJob() throws Exception {
        when(requestUserResolver.resolveUserId(any(HttpServletRequest.class))).thenReturn(99L);
        when(appJobMapper.selectById(8L)).thenReturn(job(8L, 22L));

        mockMvc.perform(get("/api/jobs/8"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    @DisplayName("普通用户不能重试他人的作业")
    void shouldRejectRetryingOtherUsersJob() throws Exception {
        when(requestUserResolver.resolveUserId(any(HttpServletRequest.class))).thenReturn(11L);
        when(adminRoleService.isAdmin(11L)).thenReturn(false);
        when(appJobMapper.selectById(3L)).thenReturn(job(3L, 22L));

        mockMvc.perform(post("/api/jobs/3/retry"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));

        verify(appJobService, never()).retryJob(3L, 11L, false);
    }

    @Test
    @DisplayName("普通用户可以读取公开作业详情")
    void shouldAllowOrdinaryUserReadingPublicJob() throws Exception {
        when(requestUserResolver.resolveUserId(any(HttpServletRequest.class))).thenReturn(11L);
        AppJob publicJob = job(5L, 22L);
        publicJob.setScope("PUBLIC");
        when(appJobMapper.selectById(5L)).thenReturn(publicJob);

        mockMvc.perform(get("/api/jobs/5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(5))
                .andExpect(jsonPath("$.data.scope").value("PUBLIC"));
    }

    @Test
    @DisplayName("普通用户不能重试公开作业")
    void shouldRejectOrdinaryUserRetryingPublicJob() throws Exception {
        when(requestUserResolver.resolveUserId(any(HttpServletRequest.class))).thenReturn(11L);
        when(adminRoleService.isAdmin(11L)).thenReturn(false);
        AppJob publicJob = job(6L, null);
        publicJob.setScope("PUBLIC");
        when(appJobMapper.selectById(6L)).thenReturn(publicJob);

        mockMvc.perform(post("/api/jobs/6/retry"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));

        verify(appJobService, never()).retryJob(6L, 11L, false);
    }

    @Test
    @DisplayName("管理员不能重试其他账号的私有作业")
    void shouldRejectAdminRetryingOtherUsersPrivateJob() throws Exception {
        when(requestUserResolver.resolveUserId(any(HttpServletRequest.class))).thenReturn(99L);
        when(adminRoleService.isAdmin(99L)).thenReturn(true);
        when(appJobMapper.selectById(4L)).thenReturn(job(4L, 22L));

        mockMvc.perform(post("/api/jobs/4/retry"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));

        verify(appJobService, never()).retryJob(4L, 99L, true);
        verify(appJobRecoveryService, never()).dispatchJob(4L);
    }

    @Test
    @DisplayName("管理员可以重试公共题库作业")
    void shouldAllowAdminRetryingPublicJob() throws Exception {
        when(requestUserResolver.resolveUserId(any(HttpServletRequest.class))).thenReturn(99L);
        when(adminRoleService.isAdmin(99L)).thenReturn(true);
        AppJob publicJob = job(9L, 22L);
        publicJob.setScope("PUBLIC");
        when(appJobMapper.selectById(9L)).thenReturn(publicJob);
        when(appJobService.retryJob(9L, 99L, true)).thenReturn(true);

        mockMvc.perform(post("/api/jobs/9/retry"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(appJobService).retryJob(9L, 99L, true);
        verify(appJobRecoveryService).dispatchJob(9L);
    }

    @Test
    @DisplayName("普通用户重试自己的可重试作业后立即重新投递")
    void shouldDispatchOwnedRetryableJobAfterRetry() throws Exception {
        when(requestUserResolver.resolveUserId(any(HttpServletRequest.class))).thenReturn(11L);
        when(adminRoleService.isAdmin(11L)).thenReturn(false);
        when(appJobMapper.selectById(7L)).thenReturn(job(7L, 11L));
        when(appJobService.retryJob(7L, 11L, false)).thenReturn(true);

        mockMvc.perform(post("/api/jobs/7/retry"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(appJobService).retryJob(7L, 11L, false);
        verify(appJobRecoveryService).dispatchJob(7L);
    }

    private AppJob job(Long id, Long ownerUserId) {
        AppJob job = new AppJob();
        job.setId(id);
        job.setOwnerUserId(ownerUserId);
        job.setJobType("QUESTION_BANK_IMPORT");
        job.setScope("USER");
        job.setStatus("FAILED");
        job.setStage("VALIDATE");
        job.setProgress(25);
        job.setPayloadJson("{\"secret\":true}");
        job.setRetryable(true);
        job.setRetryCount(1);
        return job;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private ArgumentCaptor<QueryWrapper<AppJob>> queryCaptor() {
        return ArgumentCaptor.forClass((Class) QueryWrapper.class);
    }
}

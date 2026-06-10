package com.interview.controller;

import com.interview.service.ResumeService;
import com.interview.service.UsageQuotaService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@DisplayName("ResumeController security checks")
class ResumeControllerSecurityTest {

    @Test
    @DisplayName("拒绝非 PDF 简历上传且不消耗额度")
    void shouldRejectNonPdfResumeUploadBeforeQuotaAndParsing() throws Exception {
        ResumeController controller = new ResumeController();
        ResumeService resumeService = mock(ResumeService.class);
        UsageQuotaService usageQuotaService = mock(UsageQuotaService.class);
        ReflectionTestUtils.setField(controller, "resumeService", resumeService);
        ReflectionTestUtils.setField(controller, "usageQuotaService", usageQuotaService);

        MockMultipartFile file = new MockMultipartFile(
                "file", "resume.txt", "text/plain", "not a pdf".getBytes());
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute("currentUserId", 7L);

        var result = controller.parseResume(file, "AI大模型", request);

        assertThat(result.getCode()).isEqualTo(400);
        assertThat(result.getMsg()).contains("PDF");
        verify(usageQuotaService, never()).consume(7L, UsageQuotaService.RESUME_PARSE);
        verify(resumeService, never()).parseAndAnalyze(anyLong(), eq(file));
    }

    @Test
    @DisplayName("拒绝超过大小限制的简历上传且不消耗额度")
    void shouldRejectOversizedResumeUploadBeforeQuotaAndParsing() throws Exception {
        ResumeController controller = new ResumeController();
        ResumeService resumeService = mock(ResumeService.class);
        UsageQuotaService usageQuotaService = mock(UsageQuotaService.class);
        ReflectionTestUtils.setField(controller, "resumeService", resumeService);
        ReflectionTestUtils.setField(controller, "usageQuotaService", usageQuotaService);

        byte[] content = new byte[6 * 1024 * 1024];
        content[0] = '%';
        content[1] = 'P';
        content[2] = 'D';
        content[3] = 'F';
        MockMultipartFile file = new MockMultipartFile(
                "file", "resume.pdf", "application/pdf", content);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute("currentUserId", 7L);

        var result = controller.parseResume(file, "AI大模型", request);

        assertThat(result.getCode()).isEqualTo(400);
        assertThat(result.getMsg()).contains("不能超过");
        verify(usageQuotaService, never()).consume(7L, UsageQuotaService.RESUME_PARSE);
        verify(resumeService, never()).parseAndAnalyze(anyLong(), eq(file));
    }
}

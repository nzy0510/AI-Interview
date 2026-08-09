package com.interview.controller;

import com.interview.dto.questionbank.build.QuestionBankBuildFinalizationRequest;
import com.interview.dto.questionbank.build.QuestionBankBuildFinalizationResponse;
import com.interview.dto.questionbank.build.QuestionBankBuildResponse;
import com.interview.service.RequestUserResolver;
import com.interview.service.questionbank.build.QuestionBankBuildFinalizationService;
import com.interview.service.questionbank.build.QuestionBankBuildRepairRequestService;
import com.interview.service.questionbank.build.QuestionBankBuildService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockMultipartFile;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class QuestionBankBuildControllerTest {
    @Test
    void shouldForwardMultipartBuildToOwnerScopedService() {
        QuestionBankBuildService service = mock(QuestionBankBuildService.class);
        QuestionBankBuildFinalizationService finalizationService = mock(QuestionBankBuildFinalizationService.class);
        RequestUserResolver resolver = mock(RequestUserResolver.class);
        QuestionBankBuildController controller = new QuestionBankBuildController(
                service, finalizationService, mock(QuestionBankBuildRepairRequestService.class), resolver);
        MockHttpServletRequest request = new MockHttpServletRequest();
        when(resolver.resolveUserId(any(HttpServletRequest.class))).thenReturn(7L);
        QuestionBankBuildResponse expected = new QuestionBankBuildResponse(); expected.setBuildId(99L);
        when(service.create(eq(7L), eq(55L), anyList(), eq(List.of("java")))).thenReturn(expected);

        var result = controller.create(55L, List.of(new MockMultipartFile("files", "notes.md", "text/markdown", "hello".getBytes())), List.of("java"), request);

        assertThat(result.getData()).isSameAs(expected);
        verify(service).create(eq(7L), eq(55L), anyList(), eq(List.of("java")));
    }

    @Test
    void shouldStartExplicitFinalReviewWithAuthenticatedOwnerOnly() {
        QuestionBankBuildService service = mock(QuestionBankBuildService.class);
        QuestionBankBuildFinalizationService finalizationService = mock(QuestionBankBuildFinalizationService.class);
        RequestUserResolver resolver = mock(RequestUserResolver.class);
        QuestionBankBuildController controller = new QuestionBankBuildController(
                service, finalizationService, mock(QuestionBankBuildRepairRequestService.class), resolver);
        MockHttpServletRequest servletRequest = new MockHttpServletRequest();
        when(resolver.resolveUserId(any(HttpServletRequest.class))).thenReturn(7L);
        QuestionBankBuildFinalizationRequest body = new QuestionBankBuildFinalizationRequest();
        body.setCandidateIds(List.of(101L, 102L));
        body.setExpectedReviewRevision(4L);
        QuestionBankBuildFinalizationResponse expected = new QuestionBankBuildFinalizationResponse();
        expected.setJobId(501L);
        when(finalizationService.start(7L, 55L, 99L, body)).thenReturn(expected);

        var result = controller.finalizeAndPublish(55L, 99L, body, servletRequest);

        assertThat(result.getData()).isSameAs(expected);
        verify(finalizationService).start(7L, 55L, 99L, body);
    }
}

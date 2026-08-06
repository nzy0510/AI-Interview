package com.interview.controller;

import com.interview.dto.questionbank.build.QuestionBankBuildResponse;
import com.interview.service.RequestUserResolver;
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
        RequestUserResolver resolver = mock(RequestUserResolver.class);
        QuestionBankBuildController controller = new QuestionBankBuildController(service, resolver);
        MockHttpServletRequest request = new MockHttpServletRequest();
        when(resolver.resolveUserId(any(HttpServletRequest.class))).thenReturn(7L);
        QuestionBankBuildResponse expected = new QuestionBankBuildResponse(); expected.setBuildId(99L);
        when(service.create(eq(7L), eq(55L), anyList(), eq(List.of("java")))).thenReturn(expected);

        var result = controller.create(55L, List.of(new MockMultipartFile("files", "notes.md", "text/markdown", "hello".getBytes())), List.of("java"), request);

        assertThat(result.getData()).isSameAs(expected);
        verify(service).create(eq(7L), eq(55L), anyList(), eq(List.of("java")));
    }
}

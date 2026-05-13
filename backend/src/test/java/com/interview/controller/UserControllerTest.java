package com.interview.controller;

import com.interview.common.Result;
import com.interview.entity.User;
import com.interview.service.DeveloperAccessService;
import com.interview.service.MentorService;
import com.interview.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UserControllerTest {

    @Test
    void currentUserIncludesDeveloperFlag() {
        UserController controller = new UserController();
        UserService userService = mock(UserService.class);
        MentorService mentorService = mock(MentorService.class);
        DeveloperAccessService developerAccessService = mock(DeveloperAccessService.class);
        ReflectionTestUtils.setField(controller, "userService", userService);
        ReflectionTestUtils.setField(controller, "mentorService", mentorService);
        ReflectionTestUtils.setField(controller, "developerAccessService", developerAccessService);

        User user = new User();
        user.setId(7L);
        user.setUsername("nzy333");
        user.setEmail("1525764737@qq.com");

        when(userService.getById(7L)).thenReturn(user);
        when(developerAccessService.isDeveloper(7L)).thenReturn(true);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute("currentUserId", 7L);

        Result<Map<String, Object>> result = controller.getCurrentUser(request);

        assertThat(result.getData())
                .containsEntry("username", "nzy333")
                .containsEntry("isDeveloper", true);
    }
}

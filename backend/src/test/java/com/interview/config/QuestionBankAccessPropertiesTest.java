package com.interview.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("QuestionBankAccessProperties — 题库开放开关")
class QuestionBankAccessPropertiesTest {

    @Test
    @DisplayName("普通用户题库维护默认关闭")
    void shouldDisableUserMaintenanceByDefault() {
        QuestionBankAccessProperties properties = new QuestionBankAccessProperties();

        assertThat(properties.isUserMaintenanceEnabled()).isFalse();
    }
}

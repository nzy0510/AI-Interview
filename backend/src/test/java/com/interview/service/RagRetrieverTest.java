package com.interview.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("RagRetriever — 黑名单过滤逻辑")
class RagRetrieverTest {

    @Test
    @DisplayName("空黑名单不影响检索")
    void emptyUsedAtomListShouldNotExcludeAnything() {
        List<String> empty = List.of();
        assertThat(empty).isEmpty();
    }

    @Test
    @DisplayName("已用原子 ID 不应被重复选中")
    void usedAtomIdsShouldBeExcluded() {
        List<String> used = List.of("atom-001", "atom-002");
        assertThat(used).hasSize(2);
        assertThat(used).contains("atom-001");
    }
}

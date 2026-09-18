package com.interview.service;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SessionStore — 会话存储（内存模式）")
class SessionStoreTest {

    private SessionStore store;

    @BeforeEach
    void setUp() {
        store = new SessionStore(null); // null RedisTemplate → 内存模式
    }

    @Test
    @DisplayName("保存后加载应返回相同消息")
    void shouldReturnSavedMessages() {
        List<ChatMessage> original = List.of(
                new UserMessage("你好"),
                new AiMessage("你好，欢迎参加面试")
        );
        store.save(1L, original);

        List<ChatMessage> loaded = store.load(1L);
        assertThat(loaded).hasSize(2);
        assertThat(((UserMessage) loaded.get(0)).singleText()).isEqualTo("你好");
        assertThat(((AiMessage) loaded.get(1)).text()).isEqualTo("你好，欢迎参加面试");
    }

    @Test
    @DisplayName("未保存的 recordId 应返回 null")
    void shouldReturnNullForUnknownRecord() {
        assertThat(store.load(999L)).isNull();
    }

    @Test
    @DisplayName("删除后加载应返回 null")
    void shouldReturnNullAfterDelete() {
        store.save(1L, List.of(new UserMessage("test")));
        store.delete(1L);
        assertThat(store.load(1L)).isNull();
    }

    @Test
    @DisplayName("追加保存应更新消息列表")
    void shouldUpdateOnRepeatedSave() {
        List<ChatMessage> first = new ArrayList<>();
        first.add(new UserMessage("Q1"));
        store.save(1L, first);

        List<ChatMessage> second = new ArrayList<>();
        second.add(new UserMessage("Q1"));
        second.add(new AiMessage("A1"));
        second.add(new UserMessage("Q2"));
        store.save(1L, second);

        assertThat(store.load(1L)).hasSize(3);
    }

    @Test
    @DisplayName("不同 recordId 的消息相互隔离")
    void shouldIsolateMessagesByRecordId() {
        store.save(1L, List.of(new UserMessage("record-1")));
        store.save(2L, List.of(new UserMessage("record-2")));

        assertThat(((UserMessage) store.load(1L).get(0)).singleText()).isEqualTo("record-1");
        assertThat(((UserMessage) store.load(2L).get(0)).singleText()).isEqualTo("record-2");
    }

    @Test
    @DisplayName("保存量身定制题库后加载应一致")
    void shouldSaveAndLoadTailoredQuestions() {
        List<String> questions = List.of("请解释 Spring Boot 自动配置原理", "Redis 缓存穿透如何解决");
        store.saveTailoredQuestions(1L, questions);

        List<String> loaded = store.loadTailoredQuestions(1L);
        assertThat(loaded).containsExactlyElementsOf(questions);
    }

    @Test
    @DisplayName("保存已用原子 ID 后加载应一致")
    void shouldSaveAndLoadUsedAtoms() {
        store.saveUsedAtoms(1L, new ArrayList<>(List.of("atom-001", "atom-002")));

        List<String> loaded = store.loadUsedAtoms(1L);
        assertThat(loaded).containsExactlyInAnyOrder("atom-001", "atom-002");
    }

    @Test
    @DisplayName("原子追加已用原子 ID 应去重合并")
    void shouldAtomicallyAddUsedAtomIds() {
        store.saveUsedAtoms(1L, new ArrayList<>(List.of("atom-001")));
        store.addUsedAtoms(1L, List.of("atom-002", "atom-003"));
        store.addUsedAtoms(1L, List.of("atom-002")); // 重复

        List<String> loaded = store.loadUsedAtoms(1L);
        assertThat(loaded).hasSize(3).contains("atom-001", "atom-002", "atom-003");
    }

    @Test
    @DisplayName("并发追加已用原子 ID 不应丢失数据")
    void shouldNotLoseDataUnderConcurrentAdds() throws Exception {
        store.saveUsedAtoms(1L, new ArrayList<>());

        Thread t1 = new Thread(() -> {
            for (int i = 0; i < 100; i++) store.addUsedAtoms(1L, List.of("t1-" + i));
        });
        Thread t2 = new Thread(() -> {
            for (int i = 0; i < 100; i++) store.addUsedAtoms(1L, List.of("t2-" + i));
        });
        t1.start(); t2.start();
        t1.join(); t2.join();

        assertThat(store.loadUsedAtoms(1L)).hasSize(200);
    }
}

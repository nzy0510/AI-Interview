package com.interview.service.questionbank;

import com.interview.mapper.AppJobMapper;
import com.interview.mapper.KnowledgeAtomMapper;
import com.interview.mapper.KnowledgeAtomVersionMapper;
import com.interview.mapper.KnowledgeSourceFileMapper;
import com.interview.service.AdminRoleService;
import com.interview.service.AppJobDispatcher;
import com.interview.service.AppJobRecoveryService;
import com.interview.service.AppJobService;
import com.interview.service.UserLlmConfigService;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.task.TaskExecutor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class KnowledgeAtomJobContextTest {

    @Test
    void shouldWireAtomGenerationJobHandlerWithoutCircularDependency() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.registerBean(AppJobMapper.class, () -> mock(AppJobMapper.class));
            context.registerBean(KnowledgeSourceFileMapper.class, () -> mock(KnowledgeSourceFileMapper.class));
            context.registerBean(KnowledgeAtomMapper.class, () -> mock(KnowledgeAtomMapper.class));
            context.registerBean(KnowledgeAtomVersionMapper.class, () -> mock(KnowledgeAtomVersionMapper.class));
            context.registerBean(FileStorageService.class, () -> mock(FileStorageService.class));
            context.registerBean(UserLlmConfigService.class, () -> mock(UserLlmConfigService.class));
            context.registerBean(AdminRoleService.class, () -> mock(AdminRoleService.class));
            context.registerBean(KnowledgeAtomAiClient.class, () -> mock(KnowledgeAtomAiClient.class));
            context.registerBean(QuestionBankService.class, () -> mock(QuestionBankService.class));
            context.registerBean(AppJobService.class, () -> mock(AppJobService.class));
            context.registerBean("appJobTaskExecutor", TaskExecutor.class, () -> Runnable::run);
            context.register(AppJobDispatcher.class);
            context.register(AppJobRecoveryService.class);
            context.register(KnowledgeAtomWorkflowService.class);
            context.register(AtomGenerationJobHandler.class);

            context.refresh();

            assertThat(context.getBean(AtomGenerationJobHandler.class)).isNotNull();
        }
    }
}

package com.interview.service.questionbank;

import dev.langchain4j.model.embedding.EmbeddingModel;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class QdrantVectorServiceSpringContextTest {

    @Test
    void shouldCreateBeanWithEmbeddingModelConstructor() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.registerBean(EmbeddingModel.class, () -> mock(EmbeddingModel.class));
            context.register(QdrantVectorService.class);

            context.refresh();

            assertThat(context.getBean(QdrantVectorService.class)).isNotNull();
        }
    }
}

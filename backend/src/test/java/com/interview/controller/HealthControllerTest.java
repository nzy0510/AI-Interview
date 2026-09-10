package com.interview.controller;

import com.interview.service.questionbank.QdrantVectorService;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class HealthControllerTest {
    @Test
    void shouldReportTheAuthenticatedCollectionAvailability() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject("SELECT 1", Integer.class)).thenReturn(1);
        QdrantVectorService qdrant = mock(QdrantVectorService.class);
        HealthController controller = new HealthController(jdbc, qdrant);
        ReflectionTestUtils.setField(controller, "qdrantEnabled", true);

        when(qdrant.isAvailable()).thenReturn(false, true);
        assertThat(controller.health().getData()).containsEntry("qdrant", "DOWN").containsEntry("status", "DEGRADED");
        assertThat(controller.health().getData()).containsEntry("qdrant", "UP").containsEntry("status", "UP");
    }

    @Test
    void shouldNotContactQdrantWhenDisabled() {
        QdrantVectorService qdrant = mock(QdrantVectorService.class);
        HealthController controller = new HealthController(mock(JdbcTemplate.class), qdrant);
        assertThat(controller.health().getData()).containsEntry("qdrant", "DISABLED");
        verifyNoInteractions(qdrant);
    }
}

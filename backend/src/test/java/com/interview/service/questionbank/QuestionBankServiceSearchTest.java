package com.interview.service.questionbank;

import com.interview.config.PositionCategoryConfig;
import com.interview.dto.questionbank.QuestionBankSearchRequest;
import com.interview.dto.questionbank.QuestionBankSearchResponse;
import com.interview.entity.KnowledgeAtom;
import com.interview.mapper.KnowledgeAtomImportBatchMapper;
import com.interview.mapper.KnowledgeAtomMapper;
import com.interview.mapper.KnowledgeAtomVersionMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@DisplayName("QuestionBankService search")
@ExtendWith(MockitoExtension.class)
class QuestionBankServiceSearchTest {

    @Mock
    private KnowledgeAtomMapper atomMapper;

    @Mock
    private KnowledgeAtomVersionMapper versionMapper;

    @Mock
    private KnowledgeAtomImportBatchMapper batchMapper;

    @Mock
    private PositionCategoryConfig categoryConfig;

    @Mock
    private QdrantVectorService qdrantVectorService;

    private QuestionBankService service;

    @BeforeEach
    void setUp() {
        service = new QuestionBankService(atomMapper, versionMapper, batchMapper, categoryConfig, qdrantVectorService);
    }

    @Test
    @DisplayName("returns QDRANT_VECTOR when a vector hit loads a published atom")
    void shouldReturnQdrantVectorStrategy() {
        QuestionBankSearchRequest request = request("HashMap collision handling");
        KnowledgeAtom atom = publishedAtom("java-hashmap");
        when(qdrantVectorService.search(any(), any(), any(), any(Integer.class)))
                .thenReturn(List.of(new QdrantVectorService.VectorHit(atom.getAtomId(), 0.91)));
        when(atomMapper.selectList(any())).thenReturn(List.of(atom));

        QuestionBankSearchResponse response = service.searchWithMetadata(request);

        assertThat(response.getStrategy()).isEqualTo("QDRANT_VECTOR");
        assertThat(response.getResults())
                .extracting(result -> result.getAtomId())
                .containsExactly(atom.getAtomId());
    }

    @Test
    @DisplayName("returns MYSQL_FALLBACK when no usable vector hit exists")
    void shouldReturnMysqlFallbackStrategy() {
        QuestionBankSearchRequest request = request("HashMap collision handling");
        when(qdrantVectorService.search(any(), any(), any(), any(Integer.class))).thenReturn(List.of());
        when(atomMapper.selectList(any())).thenReturn(List.of());

        QuestionBankSearchResponse response = service.searchWithMetadata(request);

        assertThat(response.getStrategy()).isEqualTo("MYSQL_FALLBACK");
        assertThat(response.getResults()).isEmpty();
        verify(atomMapper).selectList(any());
    }

    @Test
    @DisplayName("returns MYSQL_FALLBACK when Qdrant contains a stale atom ID")
    void shouldFallbackWhenQdrantReturnsStaleAtomId() {
        QuestionBankSearchRequest request = request("HashMap collision handling");
        when(qdrantVectorService.search(any(), any(), any(), any(Integer.class)))
                .thenReturn(List.of(new QdrantVectorService.VectorHit("stale-atom", 0.91)));
        when(atomMapper.selectList(any())).thenReturn(List.of());

        QuestionBankSearchResponse response = service.searchWithMetadata(request);

        assertThat(response.getStrategy()).isEqualTo("MYSQL_FALLBACK");
        assertThat(response.getResults()).isEmpty();
    }

    @Test
    @DisplayName("returns SKIPPED for a short query")
    void shouldSkipShortQuery() {
        QuestionBankSearchResponse response = service.searchWithMetadata(request("ab"));

        assertThat(response.getStrategy()).isEqualTo("SKIPPED");
        assertThat(response.getResults()).isEmpty();
        verifyNoInteractions(atomMapper, categoryConfig, qdrantVectorService);
    }

    private QuestionBankSearchRequest request(String query) {
        QuestionBankSearchRequest request = new QuestionBankSearchRequest();
        request.setQuery(query);
        return request;
    }

    private KnowledgeAtom publishedAtom(String atomId) {
        KnowledgeAtom atom = new KnowledgeAtom();
        atom.setAtomId(atomId);
        atom.setSubject("HashMap");
        atom.setCategory("JAVA");
        atom.setDifficulty("MEDIUM");
        atom.setPrinciples("Uses buckets and resolves collisions.");
        atom.setStatus(QuestionBankService.STATUS_PUBLISHED);
        return atom;
    }
}

package com.interview.service.questionbank;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.interview.config.PositionCategoryConfig;
import com.interview.dto.questionbank.QuestionBankImportRequest;
import com.interview.dto.questionbank.QuestionBankImportResult;
import com.interview.entity.KnowledgeAtom;
import com.interview.entity.KnowledgeAtomImportBatch;
import com.interview.entity.KnowledgeAtomVersion;
import com.interview.mapper.KnowledgeAtomImportBatchMapper;
import com.interview.mapper.KnowledgeAtomMapper;
import com.interview.mapper.KnowledgeAtomVersionMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@DisplayName("QuestionBankService — import lifecycle contract")
@ExtendWith(MockitoExtension.class)
class QuestionBankImportContractTest {

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
    @DisplayName("校验结果与共享 fixture 保持一致")
    void shouldValidateImportFixtures() throws Exception {
        JSONObject errors = fixtureObject("golden-errors.json");

        assertThat(service.validateImportPackage(fixtureRequest("valid-draft.json"))).isEmpty();
        assertThat(service.validateImportPackage(fixtureRequest("invalid-empty-atoms.json")))
                .containsExactlyElementsOf(errors.getList("emptyAtoms", String.class));
        assertThat(service.validateImportPackage(fixtureRequest("invalid-missing-principles.json")))
                .containsExactlyElementsOf(errors.getList("missingPrinciples", String.class));
        assertThat(service.validateImportPackage(fixtureRequest("invalid-duplicate-id.json")))
                .containsExactlyElementsOf(errors.getList("duplicateId", String.class));
    }

    @Test
    @DisplayName("DRY_RUN 不写批次、atom 或向量")
    void shouldNotPersistAnythingOnDryRun() throws Exception {
        QuestionBankImportRequest request = fixtureRequest("valid-draft.json");
        request.setMode("DRY_RUN");

        QuestionBankImportResult result = service.importBatch(request);

        assertThat(result.getBatchId()).isEqualTo("qb-contract-draft");
        assertThat(result.getMode()).isEqualTo("DRY_RUN");
        assertThat(result.getReceived()).isEqualTo(1);
        assertThat(result.getImported()).isZero();
        assertThat(result.getPublished()).isZero();
        assertThat(result.getFailed()).isZero();

        verifyNoInteractions(batchMapper, atomMapper, versionMapper, qdrantVectorService);
    }

    @Test
    @DisplayName("DRAFT 导入写入稳定的 golden atom，不同步 Qdrant")
    void shouldImportDraftAtomAsGoldenShape() throws Exception {
        when(atomMapper.selectOne(any())).thenReturn(null);
        when(versionMapper.selectCount(any())).thenReturn(0L);

        QuestionBankImportResult result = service.importBatch(fixtureRequest("valid-draft.json"));

        assertThat(result.getBatchId()).isEqualTo("qb-contract-draft");
        assertThat(result.getMode()).isEqualTo("DRAFT");
        assertThat(result.getImported()).isEqualTo(1);
        assertThat(result.getPublished()).isZero();
        assertThat(result.getFailed()).isZero();

        ArgumentCaptor<KnowledgeAtom> atomCaptor = ArgumentCaptor.forClass(KnowledgeAtom.class);
        verify(atomMapper).insert(atomCaptor.capture());
        assertGoldenAtom(atomCaptor.getValue());

        ArgumentCaptor<KnowledgeAtomVersion> versionCaptor = ArgumentCaptor.forClass(KnowledgeAtomVersion.class);
        verify(versionMapper).insert(versionCaptor.capture());
        assertThat(versionCaptor.getValue().getAtomId()).isEqualTo("contract-java-hashmap");
        assertThat(versionCaptor.getValue().getVersionNo()).isEqualTo(1);
        assertThat(versionCaptor.getValue().getChangeReason()).isEqualTo("import:qb-contract-draft");
        verify(qdrantVectorService, never()).upsert(any());
    }

    @Test
    @DisplayName("AUTO_PUBLISH 导入会发布 atom 并同步 Qdrant")
    void shouldPublishAndSyncAutoPublishedAtom() throws Exception {
        when(atomMapper.selectOne(any())).thenReturn(null);
        when(versionMapper.selectCount(any())).thenReturn(0L);
        AtomicReference<String> vectorStatusAtUpsert = new AtomicReference<>();
        when(qdrantVectorService.upsert(any())).thenAnswer(invocation -> {
            KnowledgeAtom atom = invocation.getArgument(0);
            vectorStatusAtUpsert.set(atom.getVectorStatus());
            return true;
        });

        QuestionBankImportResult result = service.importBatch(fixtureRequest("valid-auto-publish.json"));

        assertThat(result.getBatchId()).isEqualTo("qb-contract-auto-publish");
        assertThat(result.getMode()).isEqualTo("AUTO_PUBLISH");
        assertThat(result.getImported()).isEqualTo(1);
        assertThat(result.getPublished()).isEqualTo(1);
        assertThat(result.getFailed()).isZero();
        assertThat(vectorStatusAtUpsert).hasValue("PENDING");

        ArgumentCaptor<KnowledgeAtom> updateCaptor = ArgumentCaptor.forClass(KnowledgeAtom.class);
        verify(atomMapper).updateById(updateCaptor.capture());
        assertThat(updateCaptor.getValue().getAtomId()).isEqualTo("contract-java-hashmap");
        assertThat(updateCaptor.getValue().getStatus()).isEqualTo("PUBLISHED");
        assertThat(updateCaptor.getValue().getVectorStatus()).isEqualTo("SYNCED");
        assertThat(updateCaptor.getValue().getLastIndexedAt()).isNotNull();
    }

    @Test
    @DisplayName("重建索引只同步 mapper 返回的已发布 atom")
    void shouldReindexPublishedAtoms() {
        KnowledgeAtom first = publishedAtom("contract-java-hashmap");
        KnowledgeAtom second = publishedAtom("contract-java-jvm");
        when(atomMapper.selectList(any())).thenReturn(List.of(first, second));
        when(qdrantVectorService.upsert(first)).thenReturn(true);
        when(qdrantVectorService.upsert(second)).thenReturn(false);

        int synced = service.reindexPublishedAtoms();

        assertThat(synced).isEqualTo(1);
        ArgumentCaptor<KnowledgeAtom> updateCaptor = ArgumentCaptor.forClass(KnowledgeAtom.class);
        verify(atomMapper, times(2)).updateById(updateCaptor.capture());
        assertThat(updateCaptor.getAllValues())
                .extracting(KnowledgeAtom::getVectorStatus)
                .containsExactly("SYNCED", "FAILED");
    }

    @Test
    @DisplayName("归档删除失败时保留可重试状态")
    void shouldKeepRetryableStateWhenArchiveDeleteFails() {
        KnowledgeAtom atom = publishedAtom("contract-java-hashmap");
        when(atomMapper.selectList(any())).thenReturn(List.of(atom));
        when(versionMapper.selectCount(any())).thenReturn(0L);
        when(qdrantVectorService.delete(atom.getAtomId())).thenReturn(false);
        List<String> statusesAtUpdate = new ArrayList<>();
        doAnswer(invocation -> {
            KnowledgeAtom updated = invocation.getArgument(0);
            statusesAtUpdate.add(updated.getVectorStatus());
            return 1;
        }).when(atomMapper).updateById(any());

        Map<String, Integer> result = service.archiveAtoms(List.of(atom.getAtomId()));

        assertThat(result.get("archived")).isEqualTo(1);
        assertThat(result.get("deleted")).isZero();
        assertThat(statusesAtUpdate).containsExactly("PENDING_DELETE", "DELETE_FAILED");
    }

    @Test
    @DisplayName("调用 Qdrant 的题库方法不持有声明式数据库事务")
    void shouldNotWrapQdrantCallsInDatabaseTransactions() throws Exception {
        assertThat(QuestionBankService.class.getMethod("publishAtoms", List.class)
                .isAnnotationPresent(org.springframework.transaction.annotation.Transactional.class)).isFalse();
        assertThat(QuestionBankService.class.getMethod("archiveAtoms", List.class)
                .isAnnotationPresent(org.springframework.transaction.annotation.Transactional.class)).isFalse();
        assertThat(QuestionBankService.class.getMethod("importBatch", QuestionBankImportRequest.class)
                .isAnnotationPresent(org.springframework.transaction.annotation.Transactional.class)).isFalse();
    }

    @Test
    @DisplayName("未同步重试同时补偿发布写入和归档删除")
    void shouldReconcilePendingUpsertsAndDeletes() {
        KnowledgeAtom published = publishedAtom("contract-java-hashmap");
        published.setVectorStatus("FAILED");
        KnowledgeAtom archived = publishedAtom("contract-java-old");
        archived.setStatus("ARCHIVED");
        archived.setVectorStatus("DELETE_FAILED");
        when(atomMapper.selectList(any())).thenReturn(List.of(published), List.of(archived));
        when(qdrantVectorService.upsert(published)).thenReturn(true);
        when(qdrantVectorService.delete(archived.getAtomId())).thenReturn(true);

        Map<String, Integer> result = service.reindexUnsyncedPublishedAtomResult();

        assertThat(result)
                .containsEntry("synced", 1)
                .containsEntry("deleted", 1)
                .containsEntry("failed", 0);
        assertThat(archived.getVectorStatus()).isEqualTo("DELETED");
    }

    @Test
    @DisplayName("版本号并发冲突时重新分配版本号")
    void shouldRetryVersionAllocationAfterDuplicateKey() throws Exception {
        when(atomMapper.selectOne(any())).thenReturn(null);
        when(versionMapper.selectCount(any())).thenReturn(0L, 1L);
        when(versionMapper.insert(any()))
                .thenThrow(new DuplicateKeyException("duplicate atom version"))
                .thenReturn(1);

        QuestionBankImportResult result = service.importBatch(fixtureRequest("valid-draft.json"));

        assertThat(result.getFailed()).isZero();
        ArgumentCaptor<KnowledgeAtomVersion> captor = ArgumentCaptor.forClass(KnowledgeAtomVersion.class);
        verify(versionMapper, times(2)).insert(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(KnowledgeAtomVersion::getVersionNo)
                .containsExactly(1, 2);
    }

    private void assertGoldenAtom(KnowledgeAtom atom) throws IOException {
        JSONObject golden = fixtureObject("golden-atom.json");
        assertThat(atom.getAtomId()).isEqualTo(golden.getString("atomId"));
        assertThat(atom.getSubject()).isEqualTo(golden.getString("subject"));
        assertThat(atom.getCategory()).isEqualTo(golden.getString("category"));
        assertThat(atom.getDifficulty()).isEqualTo(golden.getString("difficulty"));
        assertThat(atom.getTagsJson()).isEqualTo(golden.getString("tagsJson"));
        assertThat(atom.getPrinciples()).isEqualTo(golden.getString("principles"));
        assertThat(atom.getPitfalls()).isEqualTo(golden.getString("pitfalls"));
        assertThat(atom.getFollowUpPathsJson()).isEqualTo(golden.getString("followUpPathsJson"));
        assertThat(atom.getStatus()).isEqualTo(golden.getString("status"));
        assertThat(atom.getSourceRef()).isEqualTo(golden.getString("sourceRef"));
        assertThat(atom.getChecksum()).isEqualTo(golden.getString("checksum"));
        assertThat(atom.getVectorStatus()).isEqualTo(golden.getString("vectorStatus"));
    }

    private KnowledgeAtom publishedAtom(String atomId) {
        KnowledgeAtom atom = new KnowledgeAtom();
        atom.setAtomId(atomId);
        atom.setSubject("subject " + atomId);
        atom.setCategory("java");
        atom.setPrinciples("principles");
        atom.setStatus("PUBLISHED");
        atom.setVectorStatus("PENDING");
        return atom;
    }

    private QuestionBankImportRequest fixtureRequest(String name) throws IOException {
        return JSON.parseObject(Files.readString(fixturePath(name)), QuestionBankImportRequest.class);
    }

    private JSONObject fixtureObject(String name) throws IOException {
        return JSON.parseObject(Files.readString(fixturePath(name)));
    }

    private Path fixturePath(String name) {
        Path cwd = Path.of("").toAbsolutePath();
        List<Path> candidates = List.of(
                cwd.resolve("question_bank_imports/fixtures/import-lifecycle/" + name),
                cwd.resolve("../question_bank_imports/fixtures/import-lifecycle/" + name).normalize()
        );
        return candidates.stream()
                .filter(Files::exists)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Missing contract fixture: " + name));
    }
}

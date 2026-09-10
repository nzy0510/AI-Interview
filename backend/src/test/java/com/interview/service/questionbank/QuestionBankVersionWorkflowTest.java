package com.interview.service.questionbank;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.interview.config.PositionCategoryConfig;
import com.interview.config.QuestionBankAccessProperties;
import com.interview.entity.KnowledgeAtom;
import com.interview.entity.KnowledgeAtomVersion;
import com.interview.mapper.KnowledgeAtomImportBatchMapper;
import com.interview.mapper.KnowledgeAtomMapper;
import com.interview.mapper.KnowledgeAtomVersionMapper;
import com.interview.service.AdminRoleService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DuplicateKeyException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class QuestionBankVersionWorkflowTest {

    private final Map<Long, KnowledgeAtom> atoms = new HashMap<>();
    private final Map<String, TreeMap<Integer, KnowledgeAtomVersion>> versions = new HashMap<>();
    private KnowledgeAtomWorkflowService workflow;
    private QuestionBankService questionBank;
    private Runnable afterNextAtomRead;
    private Integer repeatableReadVersionLimit;
    private boolean rejectHistoryRangeLocks;

    @BeforeEach
    void setUp() {
        KnowledgeAtomMapper atomMapper = mock(KnowledgeAtomMapper.class, invocation -> {
            switch (invocation.getMethod().getName()) {
                case "selectById": {
                    KnowledgeAtom result = copy(atoms.get(invocation.getArgument(0)), KnowledgeAtom.class);
                    if (afterNextAtomRead != null) {
                        Runnable callback = afterNextAtomRead;
                        afterNextAtomRead = null;
                        callback.run();
                    }
                    return result;
                }
                case "selectOne": {
                    QueryWrapper<KnowledgeAtom> query = invocation.getArgument(0);
                    query.getSqlSegment();
                    Long id = query.getParamNameValuePairs().values().stream()
                            .filter(Long.class::isInstance).map(Long.class::cast).findFirst().orElseThrow();
                    return copy(atoms.get(id), KnowledgeAtom.class);
                }
                case "insert": {
                    KnowledgeAtom atom = invocation.getArgument(0);
                    atom.setId(2L);
                    atoms.put(atom.getId(), copy(atom, KnowledgeAtom.class));
                    return 1;
                }
                case "updateById": {
                    KnowledgeAtom atom = invocation.getArgument(0);
                    KnowledgeAtom updated = copy(atom, KnowledgeAtom.class);
                    if (updated.getCurrentVersionNo() == null) {
                        updated.setCurrentVersionNo(atoms.get(atom.getId()).getCurrentVersionNo());
                    }
                    atoms.put(atom.getId(), updated);
                    return 1;
                }
                case "update": {
                    UpdateWrapper<KnowledgeAtom> update = invocation.getArgument(1);
                    update.getSqlSegment();
                    Long id = update.getParamNameValuePairs().values().stream()
                            .filter(Long.class::isInstance).map(Long.class::cast).findFirst().orElseThrow();
                    int nextVersion = update.getParamNameValuePairs().values().stream()
                            .filter(Integer.class::isInstance).map(Integer.class::cast).findFirst().orElseThrow();
                    KnowledgeAtom atom = atoms.get(id);
                    if (atom.getCurrentVersionNo() != null && atom.getCurrentVersionNo() >= nextVersion) return 0;
                    atom.setCurrentVersionNo(nextVersion);
                    return 1;
                }
                case "selectList": {
                    QueryWrapper<KnowledgeAtom> query = invocation.getArgument(0);
                    query.getSqlSegment();
                    return atoms.values().stream()
                            .filter(atom -> query.getParamNameValuePairs().containsValue(atom.getAtomId()))
                            .map(atom -> copy(atom, KnowledgeAtom.class)).toList();
                }
                default:
                    return org.mockito.Answers.RETURNS_DEFAULTS.answer(invocation);
            }
        });
        KnowledgeAtomVersionMapper versionMapper = mock(KnowledgeAtomVersionMapper.class, invocation -> {
            switch (invocation.getMethod().getName()) {
                case "selectOne": {
                    QueryWrapper<KnowledgeAtomVersion> query = invocation.getArgument(0);
                    boolean locking = query.getSqlSegment().contains("FOR UPDATE");
                    if (rejectHistoryRangeLocks) {
                        assertThat(locking).as("version history must not acquire empty-range locks").isFalse();
                    }
                    TreeMap<Integer, KnowledgeAtomVersion> history = history(query);
                    if (!locking && repeatableReadVersionLimit != null) {
                        history = new TreeMap<>(history.headMap(repeatableReadVersionLimit, true));
                    }
                    return history.isEmpty() ? null : history.lastEntry().getValue();
                }
                case "insert": {
                    KnowledgeAtomVersion version = invocation.getArgument(0);
                    TreeMap<Integer, KnowledgeAtomVersion> history = versions.computeIfAbsent(
                            version.getAtomId(), key -> new TreeMap<>());
                    if (history.containsKey(version.getVersionNo())) {
                        throw new DuplicateKeyException("duplicate atom version");
                    }
                    history.put(version.getVersionNo(), copy(version, KnowledgeAtomVersion.class));
                    return 1;
                }
                default:
                    return org.mockito.Answers.RETURNS_DEFAULTS.answer(invocation);
            }
        });
        QdrantVectorService qdrant = mock(QdrantVectorService.class);
        when(qdrant.upsert(any())).thenReturn(true);
        when(qdrant.delete(any())).thenReturn(true);
        questionBank = new QuestionBankService(atomMapper, versionMapper,
                mock(KnowledgeAtomImportBatchMapper.class), new PositionCategoryConfig(), qdrant);
        AdminRoleService roles = mock(AdminRoleService.class);
        when(roles.isAdmin(8L)).thenReturn(true);
        QuestionBankAccessProperties access = new QuestionBankAccessProperties();
        access.setUserMaintenanceEnabled(false);
        workflow = new KnowledgeAtomWorkflowService(atomMapper, versionMapper, roles, questionBank, access);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void adminCanEditPublishedAtomAndPublishItsDraftWithoutVersionConflicts(boolean editDraftAgain) {
        KnowledgeAtom published = publishedAtom();
        atoms.put(published.getId(), published);

        KnowledgeAtomResponse draft = workflow.updateAtom(1L, 8L, patch("first revision"));
        assertThat(draft.currentVersionNo()).isEqualTo(2);
        if (editDraftAgain) {
            draft = workflow.updateAtom(draft.id(), 8L, patch("second revision"));
            assertThat(draft.currentVersionNo()).isEqualTo(3);
        }
        String draftAtomId = atoms.get(draft.id()).getAtomId();
        Map<String, Integer> result = questionBank.publishAtoms(List.of(draftAtomId), publicScope());

        assertThat(result).containsEntry("published", 1).containsEntry("synced", 1);
        KnowledgeAtomResponse current = workflow.getAtom(draft.id(), 8L);
        assertThat(current.status()).isEqualTo("PUBLISHED");
        assertThat(current.currentVersionNo()).isEqualTo(editDraftAgain ? 4 : 3);
        assertThat(workflow.getAtom(1L, 8L).status()).isEqualTo("ARCHIVED");
        assertThat(versions.get(draftAtomId).values())
                .extracting(KnowledgeAtomVersion::getVersionNo)
                .containsExactlyElementsOf(editDraftAgain ? List.of(2, 3, 4) : List.of(2, 3));
        assertThat(versions.get(draftAtomId).values())
                .extracting(version -> JSON.parseObject(version.getSnapshotJson(), KnowledgeAtom.class)
                        .getCurrentVersionNo())
                .containsExactlyElementsOf(editDraftAgain ? List.of(2, 3, 4) : List.of(2, 3));
    }

    @Test
    void newDraftDoesNotAcquireLocksOnAnEmptyVersionHistoryRange() {
        rejectHistoryRangeLocks = true;
        atoms.put(1L, publishedAtom());

        KnowledgeAtomResponse draft = workflow.updateAtom(1L, 8L, patch("new draft"));

        assertThat(draft.status()).isEqualTo("DRAFT");
        assertThat(draft.currentVersionNo()).isEqualTo(2);
    }

    @Test
    void editingAfterAConcurrentCommitUsesTheLatestAtomVersionEvenWithStaleHistoryReads() {
        KnowledgeAtom atom = publishedAtom();
        atom.setStatus("DRAFT");
        atom.setPublicationStatus("DRAFT");
        atoms.put(1L, atom);
        rememberVersion(atom, 1);
        repeatableReadVersionLimit = 1;
        afterNextAtomRead = () -> {
            atoms.get(1L).setCurrentVersionNo(7);
            rememberVersion(atom, 7);
        };

        KnowledgeAtomResponse edited = workflow.updateAtom(1L, 8L, patch("latest edit"));

        assertThat(edited.currentVersionNo()).isEqualTo(8);
        assertThat(workflow.getAtom(1L, 8L).currentVersionNo()).isEqualTo(8);
        assertThat(versions.get(atom.getAtomId()).keySet()).containsExactly(1, 7, 8);
    }

    @ParameterizedTest
    @ValueSource(ints = {3, 12})
    void adminCanContinueSparseHistoryWithoutReusingOrDecreasingVersionNumbers(int currentVersion) {
        KnowledgeAtom atom = publishedAtom();
        atom.setStatus("DRAFT");
        atom.setPublicationStatus("DRAFT");
        atom.setCurrentVersionNo(currentVersion);
        atoms.put(atom.getId(), atom);
        for (int versionNo : List.of(1, 7)) {
            rememberVersion(atom, versionNo);
        }

        KnowledgeAtomResponse edited = workflow.updateAtom(1L, 8L, patch("continued revision"));
        int expectedEditVersion = currentVersion == 3 ? 8 : 13;
        assertThat(edited.currentVersionNo()).isEqualTo(expectedEditVersion);
        assertThat(questionBank.publishAtoms(List.of(atom.getAtomId()), publicScope()))
                .containsEntry("published", 1).containsEntry("synced", 1);
        assertThat(workflow.getAtom(1L, 8L).currentVersionNo()).isEqualTo(expectedEditVersion + 1);
        assertThat(questionBank.archiveAtoms(List.of(atom.getAtomId()), publicScope()))
                .containsEntry("archived", 1).containsEntry("deleted", 1);
        assertThat(workflow.getAtom(1L, 8L).currentVersionNo()).isEqualTo(expectedEditVersion + 2);
        assertThat(versions.get(atom.getAtomId()).keySet())
                .containsExactly(1, 7, expectedEditVersion, expectedEditVersion + 1, expectedEditVersion + 2);
        KnowledgeAtomVersion last = versions.get(atom.getAtomId()).lastEntry().getValue();
        assertThat(JSON.parseObject(last.getSnapshotJson(), KnowledgeAtom.class).getCurrentVersionNo())
                .isEqualTo(expectedEditVersion + 2);
    }

    private void rememberVersion(KnowledgeAtom atom, int versionNo) {
        KnowledgeAtomVersion version = new KnowledgeAtomVersion();
        version.setAtomId(atom.getAtomId());
        version.setVersionNo(versionNo);
        KnowledgeAtom snapshot = copy(atom, KnowledgeAtom.class);
        snapshot.setCurrentVersionNo(versionNo);
        version.setSnapshotJson(JSON.toJSONString(snapshot));
        versions.computeIfAbsent(atom.getAtomId(), key -> new TreeMap<>()).put(versionNo, version);
    }

    private TreeMap<Integer, KnowledgeAtomVersion> history(QueryWrapper<KnowledgeAtomVersion> query) {
        query.getSqlSegment();
        String atomId = (String) query.getParamNameValuePairs().values().iterator().next();
        return versions.computeIfAbsent(atomId, key -> new TreeMap<>());
    }

    private static <T> T copy(T value, Class<T> type) {
        return value == null ? null : JSON.parseObject(JSON.toJSONString(value), type);
    }

    private static KnowledgeAtomPatch patch(String subject) {
        return new KnowledgeAtomPatch(subject, null, null, null, null, null, null);
    }

    private static QuestionBankImportScope publicScope() {
        return new QuestionBankImportScope("PUBLIC", null, 1L, 1L, 8L, false);
    }

    private static KnowledgeAtom publishedAtom() {
        KnowledgeAtom atom = new KnowledgeAtom();
        atom.setId(1L);
        atom.setAtomId("public-version-regression");
        atom.setSubject("original subject");
        atom.setCategory("Java");
        atom.setDifficulty("mid");
        atom.setPrinciples("original principles");
        atom.setFollowUpPathsJson("[\"first follow-up\",\"second follow-up\"]");
        atom.setStatus("PUBLISHED");
        atom.setPublicationStatus("PUBLISHED");
        atom.setReviewStatus("PASS");
        atom.setVectorStatus("SYNCED");
        atom.setScope("PUBLIC");
        atom.setPositionId(1L);
        atom.setKnowledgeBaseId(1L);
        atom.setSourceRef("public reference");
        atom.setSourceEvidenceJson("[{\"quote\":\"public evidence\"}]");
        atom.setCurrentVersionNo(1);
        return atom;
    }
}

package com.interview.service.questionbank;

import com.interview.entity.AppJob;
import com.interview.entity.KnowledgeAtom;
import com.interview.entity.KnowledgeAtomVersion;
import com.interview.entity.KnowledgeSourceFile;
import com.interview.exception.LlmProviderRequiredException;
import com.interview.mapper.KnowledgeAtomMapper;
import com.interview.mapper.KnowledgeAtomVersionMapper;
import com.interview.mapper.KnowledgeSourceFileMapper;
import com.interview.service.AdminRoleService;
import com.interview.service.UserLlmConfigService;
import com.interview.service.UserLlmRuntimeConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("KnowledgeAtomWorkflowService — LLM 原子生成与发布工作流")
@ExtendWith(MockitoExtension.class)
class KnowledgeAtomWorkflowServiceTest {

    @Mock
    private KnowledgeSourceFileMapper sourceFileMapper;

    @Mock
    private KnowledgeAtomMapper atomMapper;

    @Mock
    private KnowledgeAtomVersionMapper versionMapper;

    @Mock
    private FileStorageService fileStorageService;

    @Mock
    private UserLlmConfigService userLlmConfigService;

    @Mock
    private AdminRoleService adminRoleService;

    @Mock
    private KnowledgeAtomAiClient aiClient;

    @Mock
    private QuestionBankService questionBankService;

    private KnowledgeAtomWorkflowService service;

    @BeforeEach
    void setUp() {
        service = new KnowledgeAtomWorkflowService(
                sourceFileMapper,
                atomMapper,
                versionMapper,
                fileStorageService,
                userLlmConfigService,
                adminRoleService,
                aiClient,
                questionBankService
        );
    }

    @Test
    @DisplayName("生成作业最多落库 100 条二审后的草稿原子")
    void shouldPersistAtMostOneHundredReviewedDraftAtoms() throws Exception {
        KnowledgeSourceFile sourceFile = convertedPrivateSourceFile();
        when(sourceFileMapper.selectById(10L)).thenReturn(sourceFile);
        when(fileStorageService.readText("markdown/10.md")).thenReturn("# Java HashMap");
        when(userLlmConfigService.requireActiveRuntimeConfig(7L)).thenReturn(runtimeConfig());
        when(aiClient.generateReviewedAtoms(any(), any())).thenReturn(new KnowledgeAtomDraftBundle(manyDrafts(101), true));

        KnowledgeAtomGenerationResult result = service.generateAtomsForJob(generationJob());

        assertThat(result.imported()).isEqualTo(100);
        assertThat(result.atomLimitReached()).isTrue();
        ArgumentCaptor<KnowledgeAtom> atomCaptor = ArgumentCaptor.forClass(KnowledgeAtom.class);
        verify(atomMapper, org.mockito.Mockito.times(100)).insert(atomCaptor.capture());
        assertThat(atomCaptor.getAllValues())
                .allSatisfy(atom -> {
                    assertThat(atom.getScope()).isEqualTo("PRIVATE");
                    assertThat(atom.getOwnerUserId()).isEqualTo(7L);
                    assertThat(atom.getPositionId()).isEqualTo(12L);
                    assertThat(atom.getKnowledgeBaseId()).isEqualTo(22L);
                    assertThat(atom.getSourceFileId()).isEqualTo(10L);
                    assertThat(atom.getStatus()).isEqualTo("DRAFT");
                    assertThat(atom.getPublicationStatus()).isEqualTo("DRAFT");
                    assertThat(atom.getVectorStatus()).isEqualTo("SKIPPED");
                    assertThat(atom.getReviewStatus()).isIn("PASS", "NEEDS_REVIEW", "REJECT");
                });
    }

    @Test
    @DisplayName("LLM 生成失败时不泄露 Bearer/API Key 到文件错误")
    void shouldSanitizeLlmFailureMessage() throws Exception {
        KnowledgeSourceFile sourceFile = convertedPrivateSourceFile();
        when(sourceFileMapper.selectById(10L)).thenReturn(sourceFile);
        when(fileStorageService.readText("markdown/10.md")).thenReturn("# Java HashMap");
        when(userLlmConfigService.requireActiveRuntimeConfig(7L)).thenReturn(runtimeConfig());
        when(aiClient.generateReviewedAtoms(any(), any()))
                .thenThrow(new RuntimeException("provider failed: Bearer sk-secret-token"));

        assertThatThrownBy(() -> service.generateAtomsForJob(generationJob()))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("[REDACTED]")
                .hasMessageNotContaining("sk-secret-token");
        assertThat(sourceFile.getErrorMessage()).doesNotContain("sk-secret-token");
        verify(sourceFileMapper).updateById(sourceFile);
    }

    @Test
    @DisplayName("REJECT 二审结果不可发布")
    void shouldRejectPublishingRejectedAtom() {
        KnowledgeAtom atom = draftAtom("REJECT");
        when(atomMapper.selectById(5L)).thenReturn(atom);

        assertThatThrownBy(() -> service.publishAtom(5L, 7L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("REJECT");

        verify(questionBankService, never()).syncAtom(any());
    }

    @Test
    @DisplayName("普通用户不能发布公共原子")
    void shouldRejectPublicAtomMutationForNormalUser() {
        KnowledgeAtom atom = draftAtom("PASS");
        atom.setScope("PUBLIC");
        atom.setOwnerUserId(null);
        when(atomMapper.selectById(5L)).thenReturn(atom);
        when(adminRoleService.isAdmin(7L)).thenReturn(false);

        assertThatThrownBy(() -> service.publishAtom(5L, 7L))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("无权访问知识原子");

        verify(questionBankService, never()).syncAtom(any());
    }

    @Test
    @DisplayName("NEEDS_REVIEW 可先应用模型建议补丁再进入 PASS 状态")
    void shouldAcceptSuggestedPatchBeforePublishing() {
        KnowledgeAtom atom = draftAtom("NEEDS_REVIEW");
        atom.setSuggestedPatchJson("""
                {"subject":"patched subject","principles":"patched principles","tags":["patched"]}
                """);
        when(atomMapper.selectById(5L)).thenReturn(atom);

        KnowledgeAtomResponse response = service.acceptSuggestedPatch(5L, 7L);

        assertThat(response.reviewStatus()).isEqualTo("PASS");
        assertThat(response.subject()).isEqualTo("patched subject");
        assertThat(response.principles()).isEqualTo("patched principles");
        assertThat(atom.getSuggestedPatchJson()).isNull();
        verify(atomMapper).updateById(atom);
    }

    @Test
    @DisplayName("编辑已发布原子时创建草稿修订，不覆盖当前可搜索版本")
    void shouldCreateDraftRevisionWhenEditingPublishedAtom() {
        KnowledgeAtom published = draftAtom("PASS");
        published.setId(8L);
        published.setAtomId("atom-published");
        published.setStatus("PUBLISHED");
        published.setPublicationStatus("PUBLISHED");
        published.setCurrentVersionNo(2);
        when(atomMapper.selectById(8L)).thenReturn(published);

        KnowledgeAtomResponse response = service.updateAtom(8L, 7L,
                new KnowledgeAtomPatch("draft revision", null, null, null, "new principles", null, null));

        ArgumentCaptor<KnowledgeAtom> insertCaptor = ArgumentCaptor.forClass(KnowledgeAtom.class);
        verify(atomMapper).insert(insertCaptor.capture());
        KnowledgeAtom draft = insertCaptor.getValue();
        assertThat(response.id()).isEqualTo(draft.getId());
        assertThat(draft.getAtomId()).isNotEqualTo("atom-published");
        assertThat(draft.getSubject()).isEqualTo("draft revision");
        assertThat(draft.getStatus()).isEqualTo("DRAFT");
        assertThat(draft.getPublicationStatus()).isEqualTo("DRAFT");
        assertThat(draft.getCurrentVersionNo()).isEqualTo(3);
    }

    @Test
    @DisplayName("PASS 草稿发布后替换为当前可搜索版本并同步向量，失败保留重试状态")
    void shouldPublishPassDraftAndKeepFailedVectorRetryable() {
        KnowledgeAtom atom = draftAtom("PASS");
        when(atomMapper.selectById(5L)).thenReturn(atom);
        when(questionBankService.syncAtom(atom)).thenAnswer(invocation -> {
            atom.setVectorStatus("FAILED");
            return false;
        });

        KnowledgeAtomResponse response = service.publishAtom(5L, 7L);

        assertThat(response.publicationStatus()).isEqualTo("PUBLISHED");
        assertThat(response.status()).isEqualTo("PUBLISHED");
        assertThat(response.vectorStatus()).isEqualTo("FAILED");
        assertThat(atom.getPublishedBy()).isEqualTo(7L);
        assertThat(atom.getPublishedAt()).isNotNull();
        verify(atomMapper).updateById(atom);
        verify(questionBankService).syncAtom(atom);
    }

    @Test
    @DisplayName("发布已生成草稿时写入下一版本，避免与生成版本冲突")
    void shouldRecordPublishAsNextVersionAfterGeneratedDraftVersion() {
        KnowledgeAtom atom = draftAtom("PASS");
        atom.setCurrentVersionNo(1);
        when(atomMapper.selectById(5L)).thenReturn(atom);
        when(questionBankService.syncAtom(atom)).thenReturn(true);

        KnowledgeAtomResponse response = service.publishAtom(5L, 7L);

        ArgumentCaptor<KnowledgeAtomVersion> versionCaptor = ArgumentCaptor.forClass(KnowledgeAtomVersion.class);
        verify(versionMapper).insert(versionCaptor.capture());
        assertThat(response.currentVersionNo()).isEqualTo(2);
        assertThat(versionCaptor.getValue().getVersionNo()).isEqualTo(2);
        assertThat(versionCaptor.getValue().getChangeReason()).isEqualTo("publish:user");
    }

    @Test
    @DisplayName("发布草稿修订且向量同步成功后归档旧发布版本")
    void shouldArchivePreviousPublishedRevisionAfterDraftRevisionPublished() {
        KnowledgeAtom atom = draftAtom("PASS");
        atom.setAtomId("atom-published-draft-abc");
        when(atomMapper.selectById(5L)).thenReturn(atom);
        when(questionBankService.syncAtom(atom)).thenAnswer(invocation -> {
            atom.setVectorStatus("SYNCED");
            return true;
        });

        KnowledgeAtomResponse response = service.publishAtom(5L, 7L);

        assertThat(response.publicationStatus()).isEqualTo("PUBLISHED");
        verify(questionBankService).archiveAtoms(List.of("atom-published"));
    }

    @Test
    @DisplayName("批量发布当前文件下 PASS 草稿并跳过不可发布原子")
    void shouldBulkPublishOnlyPublishableAtomsForSourceFile() {
        KnowledgeSourceFile sourceFile = convertedPrivateSourceFile();
        KnowledgeAtom passDraft = draftAtom("PASS");
        passDraft.setId(1L);
        KnowledgeAtom needsReview = draftAtom("NEEDS_REVIEW");
        needsReview.setId(2L);
        KnowledgeAtom published = draftAtom("PASS");
        published.setId(3L);
        published.setPublicationStatus("PUBLISHED");
        published.setStatus("PUBLISHED");

        when(sourceFileMapper.selectById(10L)).thenReturn(sourceFile);
        when(atomMapper.selectList(any())).thenReturn(List.of(passDraft, needsReview, published));
        when(questionBankService.syncAtom(passDraft)).thenAnswer(invocation -> {
            passDraft.setVectorStatus("SYNCED");
            return true;
        });

        KnowledgeAtomBulkPublishResult result = service.publishAtomsForSourceFile(10L, 7L);

        assertThat(result.matched()).isEqualTo(3);
        assertThat(result.published()).isEqualTo(1);
        assertThat(result.synced()).isEqualTo(1);
        assertThat(result.failed()).isZero();
        assertThat(result.skipped()).isEqualTo(2);
        assertThat(passDraft.getPublicationStatus()).isEqualTo("PUBLISHED");
        assertThat(passDraft.getStatus()).isEqualTo("PUBLISHED");
        verify(atomMapper).updateById(passDraft);
        verify(questionBankService).syncAtom(passDraft);
        verify(questionBankService, never()).syncAtom(needsReview);
        verify(questionBankService, never()).syncAtom(published);
    }

    private KnowledgeSourceFile convertedPrivateSourceFile() {
        KnowledgeSourceFile sourceFile = new KnowledgeSourceFile();
        sourceFile.setId(10L);
        sourceFile.setScope("PRIVATE");
        sourceFile.setOwnerUserId(7L);
        sourceFile.setPositionId(12L);
        sourceFile.setKnowledgeBaseId(22L);
        sourceFile.setOriginalFilename("java.md");
        sourceFile.setMarkdownStorageKey("markdown/10.md");
        sourceFile.setStatus("CONVERTED");
        sourceFile.setCreatedBy(7L);
        return sourceFile;
    }

    private AppJob generationJob() {
        AppJob job = new AppJob();
        job.setId(99L);
        job.setJobType("GENERATE_ATOMS");
        job.setSourceFileId(10L);
        job.setCreatedBy(7L);
        job.setClaimedBy("worker-1");
        return job;
    }

    private UserLlmRuntimeConfig runtimeConfig() {
        return new UserLlmRuntimeConfig(1L, 7L, "custom", "Custom", "https://llm.example/v1",
                "test-model", "sk-test", 0.1);
    }

    private KnowledgeAtom draftAtom(String reviewStatus) {
        KnowledgeAtom atom = new KnowledgeAtom();
        atom.setId(5L);
        atom.setAtomId("atom-draft");
        atom.setSubject("original subject");
        atom.setCategory("Java");
        atom.setDifficulty("MEDIUM");
        atom.setTagsJson("[\"java\"]");
        atom.setPrinciples("original principles");
        atom.setPitfalls("original pitfalls");
        atom.setFollowUpPathsJson("[\"follow\"]");
        atom.setStatus("DRAFT");
        atom.setPublicationStatus("DRAFT");
        atom.setReviewStatus(reviewStatus);
        atom.setVectorStatus("SKIPPED");
        atom.setScope("PRIVATE");
        atom.setOwnerUserId(7L);
        atom.setPositionId(12L);
        atom.setKnowledgeBaseId(22L);
        atom.setSourceFileId(10L);
        atom.setCurrentVersionNo(1);
        return atom;
    }

    private List<KnowledgeAtomDraft> manyDrafts(int count) {
        List<KnowledgeAtomDraft> drafts = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            drafts.add(new KnowledgeAtomDraft(
                    "subject " + i,
                    "Java",
                    "MEDIUM",
                    List.of("hashmap"),
                    "principles " + i,
                    "pitfalls " + i,
                    List.of("follow up " + i),
                    new KnowledgeAtomReviewResult(
                            i % 3 == 0 ? "PASS" : i % 3 == 1 ? "NEEDS_REVIEW" : "REJECT",
                            "reason " + i,
                            0.82,
                            i % 3 == 1 ? new KnowledgeAtomPatch("patched " + i, null, null, null, null, null, null) : null
                    )
            ));
        }
        return drafts;
    }
}

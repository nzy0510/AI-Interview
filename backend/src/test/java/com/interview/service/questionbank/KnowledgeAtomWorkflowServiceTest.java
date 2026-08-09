package com.interview.service.questionbank;

import com.interview.config.QuestionBankAccessProperties;
import com.interview.entity.KnowledgeAtom;
import com.interview.entity.KnowledgeAtomVersion;
import com.interview.mapper.KnowledgeAtomMapper;
import com.interview.mapper.KnowledgeAtomVersionMapper;
import com.interview.service.AdminRoleService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
    private KnowledgeAtomMapper atomMapper;

    @Mock
    private KnowledgeAtomVersionMapper versionMapper;

    @Mock
    private AdminRoleService adminRoleService;

    @Mock
    private QuestionBankService questionBankService;

    private QuestionBankAccessProperties accessProperties;

    private KnowledgeAtomWorkflowService service;

    @BeforeEach
    void setUp() {
        accessProperties = new QuestionBankAccessProperties();
        accessProperties.setUserMaintenanceEnabled(true);
        service = new KnowledgeAtomWorkflowService(
                atomMapper,
                versionMapper,
                adminRoleService,
                questionBankService,
                accessProperties
        );
    }

    @Test
    @DisplayName("开关关闭时普通用户无法通过旧原子接口应用补丁、修改或发布")
    void shouldDenyLegacyAtomMutationsForNormalUserWhenDisabled() {
        accessProperties.setUserMaintenanceEnabled(false);
        when(adminRoleService.isAdmin(7L)).thenReturn(false);
        KnowledgeAtomPatch patch = new KnowledgeAtomPatch(
                "updated", null, null, null, null, null, null);

        assertMutationDenied(() -> service.acceptSuggestedPatch(5L, 7L));
        assertMutationDenied(() -> service.updateAtom(5L, 7L, patch));
        assertMutationDenied(() -> service.publishAtom(5L, 7L));

        verify(atomMapper, never()).selectById(any());
        verify(atomMapper, never()).updateById(any());
        verify(questionBankService, never()).syncAtom(any());
    }

    @Test
    @DisplayName("开关关闭时管理员仍可发布公共原子，但不能修改他人私有原子")
    void shouldAllowAdminPublicMutationWithoutGrantingOtherPrivateAccess() {
        accessProperties.setUserMaintenanceEnabled(false);
        when(adminRoleService.isAdmin(8L)).thenReturn(true);
        KnowledgeAtom publicAtom = publicAtom("PASS");
        KnowledgeAtom otherPrivate = draftAtom("PASS");
        otherPrivate.setOwnerUserId(9L);
        otherPrivate.setPublishedBy(8L);
        when(atomMapper.selectById(5L)).thenReturn(publicAtom);
        when(atomMapper.selectById(6L)).thenReturn(otherPrivate);
        when(questionBankService.syncAtom(publicAtom)).thenReturn(true);

        KnowledgeAtomResponse response = service.publishAtom(5L, 8L);

        assertThat(response.publicationStatus()).isEqualTo("PUBLISHED");
        assertThatThrownBy(() -> service.publishAtom(6L, 8L))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("无权访问知识原子");
    }

    @Test
    @DisplayName("开关关闭时管理员不能通过旧原子接口修改自己的私有原子")
    void shouldDenyAdminOwnedPrivateAtomMutationsWhenDisabled() {
        accessProperties.setUserMaintenanceEnabled(false);
        when(adminRoleService.isAdmin(8L)).thenReturn(true);
        KnowledgeAtom ownPrivate = draftAtom("PASS");
        ownPrivate.setOwnerUserId(8L);
        ownPrivate.setSuggestedPatchJson("{\"subject\":\"patched\"}");
        when(atomMapper.selectById(7L)).thenReturn(ownPrivate);
        KnowledgeAtomPatch patch = new KnowledgeAtomPatch(
                "updated", null, null, null, null, null, null);

        assertMutationDenied(() -> service.acceptSuggestedPatch(7L, 8L));
        assertMutationDenied(() -> service.updateAtom(7L, 8L, patch));
        assertMutationDenied(() -> service.publishAtom(7L, 8L));

        verify(atomMapper, never()).updateById(any());
        verify(atomMapper, never()).insert(any());
        verify(questionBankService, never()).syncAtom(any());
    }

    @Test
    @DisplayName("私有题库 owner 可以在作用域 RAG 接入后发布自己的原子")
    void shouldPublishPrivateAtomForOwnerAfterScopedRag() {
        KnowledgeAtom atom = draftAtom("PASS");
        when(atomMapper.selectById(5L)).thenReturn(atom);
        when(questionBankService.syncAtom(atom)).thenReturn(true);

        KnowledgeAtomResponse response = service.publishAtom(5L, 7L);

        assertThat(response.publicationStatus()).isEqualTo("PUBLISHED");
        assertThat(response.status()).isEqualTo("PUBLISHED");
        verify(questionBankService).syncAtom(atom);
    }

    @Test
    @DisplayName("REJECT 二审结果不可发布")
    void shouldRejectPublishingRejectedAtom() {
        KnowledgeAtom atom = publicAtom("REJECT");
        when(atomMapper.selectById(5L)).thenReturn(atom);
        when(adminRoleService.isAdmin(7L)).thenReturn(true);

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
    @DisplayName("NEEDS_REVIEW 原子不能通过空补丁晋级 PASS")
    void shouldRejectEmptyPatchForNeedsReviewAtom() {
        KnowledgeAtom atom = draftAtom("NEEDS_REVIEW");
        when(atomMapper.selectById(5L)).thenReturn(atom);

        assertThatThrownBy(() -> service.updateAtom(5L, 7L,
                new KnowledgeAtomPatch(null, null, null, null, null, null, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("实际修改");
        assertThatThrownBy(() -> service.updateAtom(5L, 7L,
                new KnowledgeAtomPatch(null, null, "MID", null, null, null, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("实际修改");

        assertThat(atom.getReviewStatus()).isEqualTo("NEEDS_REVIEW");
        verify(atomMapper, never()).updateById(any());
        verify(versionMapper, never()).insert(any());
    }

    @Test
    @DisplayName("管理员对公共已发布原子应用建议补丁时创建草稿修订")
    void shouldCreateDraftRevisionWhenAcceptingPatchForPublishedPublicAtom() {
        KnowledgeAtom published = publicAtom("NEEDS_REVIEW");
        published.setId(9L);
        published.setAtomId("atom-published");
        published.setStatus("PUBLISHED");
        published.setPublicationStatus("PUBLISHED");
        published.setSuggestedPatchJson("""
                {"subject":"patched subject","principles":"patched principles"}
                """);
        when(atomMapper.selectById(9L)).thenReturn(published);
        when(adminRoleService.isAdmin(8L)).thenReturn(true);

        KnowledgeAtomResponse response = service.acceptSuggestedPatch(9L, 8L);

        ArgumentCaptor<KnowledgeAtom> insertCaptor = ArgumentCaptor.forClass(KnowledgeAtom.class);
        verify(atomMapper).insert(insertCaptor.capture());
        verify(atomMapper, never()).updateById(published);
        KnowledgeAtom draft = insertCaptor.getValue();
        assertThat(response.id()).isEqualTo(draft.getId());
        assertThat(draft.getSubject()).isEqualTo("patched subject");
        assertThat(draft.getStatus()).isEqualTo("DRAFT");
        assertThat(draft.getPublicationStatus()).isEqualTo("DRAFT");
        assertThat(draft.getScope()).isEqualTo("PUBLIC");
        assertThat(published.getStatus()).isEqualTo("PUBLISHED");
        assertThat(published.getSubject()).isEqualTo("original subject");
    }

    @Test
    @DisplayName("管理员编辑公共已发布原子时创建同作用域草稿修订，不覆盖当前可搜索版本")
    void shouldCreatePublicDraftRevisionWhenAdminEditsPublishedAtom() {
        KnowledgeAtom published = publicAtom("PASS");
        published.setId(8L);
        published.setAtomId("atom-published");
        published.setStatus("PUBLISHED");
        published.setPublicationStatus("PUBLISHED");
        published.setCurrentVersionNo(2);
        published.setSourceEvidenceJson("[{\"quote\":\"source quote\",\"pageOrSection\":\"p.2\"}]");
        when(atomMapper.selectById(8L)).thenReturn(published);
        when(adminRoleService.isAdmin(8L)).thenReturn(true);

        KnowledgeAtomResponse response = service.updateAtom(8L, 8L,
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
        assertThat(draft.getScope()).isEqualTo("PUBLIC");
        assertThat(draft.getOwnerUserId()).isNull();
        assertThat(draft.getSourceEvidenceJson()).contains("source quote").contains("p.2");
        assertThat(response.sourceEvidenceJson()).contains("source quote");
    }

    @Test
    @DisplayName("人工编辑后的最终原子不满足导入契约时拒绝标记 PASS")
    void shouldRejectInvalidManualEditBeforeMarkingPass() {
        KnowledgeAtom atom = publicAtom("PASS");
        when(atomMapper.selectById(5L)).thenReturn(atom);
        when(adminRoleService.isAdmin(8L)).thenReturn(true);

        assertThatThrownBy(() -> service.updateAtom(5L, 8L,
                new KnowledgeAtomPatch("updated", "Java", "mid", List.of("java"),
                        "principles", "pitfalls", List.of("only one follow-up"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("至少填写两条追问路径");

        verify(atomMapper, never()).updateById(any());
        verify(atomMapper, never()).insert(any());
    }

    @Test
    @DisplayName("PASS 草稿发布后替换为当前可搜索版本并同步向量，失败保留重试状态")
    void shouldPublishPassDraftAndKeepFailedVectorRetryable() {
        KnowledgeAtom atom = publicAtom("PASS");
        when(atomMapper.selectById(5L)).thenReturn(atom);
        when(adminRoleService.isAdmin(7L)).thenReturn(true);
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
    @DisplayName("PASS 草稿缺少来源证据时仍不可发布")
    void shouldRejectPublishingWithoutSourceEvidence() {
        KnowledgeAtom atom = publicAtom("PASS");
        atom.setSourceEvidenceJson("[]");
        when(atomMapper.selectById(5L)).thenReturn(atom);
        when(adminRoleService.isAdmin(7L)).thenReturn(true);

        assertThatThrownBy(() -> service.publishAtom(5L, 7L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("来源证据");

        verify(atomMapper, never()).updateById(any());
        verify(questionBankService, never()).syncAtom(any());
    }

    @Test
    @DisplayName("发布已生成草稿时写入下一版本，避免与生成版本冲突")
    void shouldRecordPublishAsNextVersionAfterGeneratedDraftVersion() {
        KnowledgeAtom atom = publicAtom("PASS");
        atom.setCurrentVersionNo(1);
        when(atomMapper.selectById(5L)).thenReturn(atom);
        when(adminRoleService.isAdmin(7L)).thenReturn(true);
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
        KnowledgeAtom atom = publicAtom("PASS");
        atom.setAtomId("atom-published-draft-abc");
        when(atomMapper.selectById(5L)).thenReturn(atom);
        when(adminRoleService.isAdmin(7L)).thenReturn(true);
        when(questionBankService.syncAtom(atom)).thenAnswer(invocation -> {
            atom.setVectorStatus("SYNCED");
            return true;
        });

        KnowledgeAtomResponse response = service.publishAtom(5L, 7L);

        assertThat(response.publicationStatus()).isEqualTo("PUBLISHED");
        verify(questionBankService).archiveAtoms(List.of("atom-published"));
    }


    private KnowledgeAtom draftAtom(String reviewStatus) {
        KnowledgeAtom atom = new KnowledgeAtom();
        atom.setId(5L);
        atom.setAtomId("atom-draft");
        atom.setSubject("original subject");
        atom.setCategory("Java");
        atom.setDifficulty("mid");
        atom.setTagsJson("[\"java\"]");
        atom.setPrinciples("original principles");
        atom.setPitfalls("original pitfalls");
        atom.setFollowUpPathsJson("[\"follow one\",\"follow two\"]");
        atom.setStatus("DRAFT");
        atom.setPublicationStatus("DRAFT");
        atom.setReviewStatus(reviewStatus);
        atom.setVectorStatus("SKIPPED");
        atom.setScope("PRIVATE");
        atom.setOwnerUserId(7L);
        atom.setPositionId(12L);
        atom.setKnowledgeBaseId(22L);
        atom.setSourceFileId(10L);
        atom.setSourceRef("source.pdf#page=2");
        atom.setSourceEvidenceJson("[{\"quote\":\"source quote\",\"pageOrSection\":\"p.2\"}]");
        atom.setCurrentVersionNo(1);
        return atom;
    }

    private KnowledgeAtom publicAtom(String reviewStatus) {
        KnowledgeAtom atom = draftAtom(reviewStatus);
        atom.setScope("PUBLIC");
        atom.setOwnerUserId(null);
        return atom;
    }

    private void assertMutationDenied(org.assertj.core.api.ThrowableAssert.ThrowingCallable callable) {
        assertThatThrownBy(callable)
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("无权访问");
    }
}

package com.interview.service.questionbank;

import com.interview.config.PositionCategoryConfig;
import com.interview.dto.questionbank.QuestionBankAtomListItem;
import com.interview.dto.questionbank.QuestionBankAtomQueryRequest;
import com.interview.dto.questionbank.QuestionBankBatchDetailResponse;
import com.interview.dto.questionbank.QuestionBankBatchListItem;
import com.interview.dto.questionbank.QuestionBankImportPreviewResponse;
import com.interview.dto.questionbank.QuestionBankImportRequest;
import com.interview.dto.questionbank.QuestionBankImportResult;
import com.interview.dto.questionbank.QuestionBankPageResponse;
import com.interview.dto.questionbank.QuestionBankSearchRequest;
import com.interview.dto.questionbank.QuestionBankSearchResponse;
import com.interview.dto.questionbank.QuestionBankSearchResult;
import com.interview.entity.KnowledgeAtom;
import com.interview.mapper.KnowledgeAtomImportBatchMapper;
import com.interview.mapper.KnowledgeAtomMapper;
import com.interview.mapper.KnowledgeAtomVersionMapper;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class QuestionBankService {

    public static final String STATUS_DRAFT = "DRAFT";
    public static final String STATUS_PUBLISHED = "PUBLISHED";
    public static final String STATUS_ARCHIVED = "ARCHIVED";

    private final QuestionBankSearchService searchService;
    private final QuestionBankCatalogService catalogService;
    private final QuestionBankLifecycleService lifecycleService;
    private final QuestionBankVectorSyncService vectorSyncService;
    private final QuestionBankImportService importService;

    public QuestionBankService(KnowledgeAtomMapper atomMapper,
                               KnowledgeAtomVersionMapper versionMapper,
                               KnowledgeAtomImportBatchMapper batchMapper,
                               PositionCategoryConfig categoryConfig,
                               QdrantVectorService qdrantVectorService) {
        this.vectorSyncService = new QuestionBankVectorSyncService(atomMapper, versionMapper, batchMapper, qdrantVectorService);
        this.searchService = new QuestionBankSearchService(atomMapper, versionMapper, batchMapper, categoryConfig, qdrantVectorService);
        this.catalogService = new QuestionBankCatalogService(atomMapper, versionMapper, batchMapper);
        this.lifecycleService = new QuestionBankLifecycleService(atomMapper, versionMapper, batchMapper, vectorSyncService);
        this.importService = new QuestionBankImportService(atomMapper, versionMapper, batchMapper, vectorSyncService);
    }

    public KnowledgeAtom getByAtomId(String atomId) {
        return importService.getByAtomId(atomId);
    }

    public List<QuestionBankSearchResult> search(QuestionBankSearchRequest request) {
        return searchService.search(request);
    }

    public QuestionBankSearchResponse searchWithMetadata(QuestionBankSearchRequest request) {
        return searchService.searchWithMetadata(request);
    }

    public List<Map<String, Object>> listCategories() {
        return catalogService.listCategories();
    }

    public List<String> validateImportPackage(QuestionBankImportRequest request) {
        return importService.validateImportPackage(request);
    }

    public QuestionBankImportPreviewResponse previewImport(QuestionBankImportRequest request) {
        return importService.previewImport(request);
    }

    public QuestionBankImportPreviewResponse previewImport(QuestionBankImportRequest request, QuestionBankImportScope scope) {
        return importService.previewImport(request, scope);
    }

    public QuestionBankPageResponse<QuestionBankAtomListItem> listAtoms(QuestionBankAtomQueryRequest request) {
        return catalogService.listAtoms(request);
    }

    public QuestionBankPageResponse<QuestionBankAtomListItem> listAtoms(QuestionBankAtomQueryRequest request,
                                                                        QuestionBankImportScope scope) {
        return catalogService.listAtoms(request, scope);
    }

    public QuestionBankPageResponse<QuestionBankBatchListItem> listBatches(int pageValue, int sizeValue) {
        return catalogService.listBatches(pageValue, sizeValue);
    }

    public QuestionBankBatchDetailResponse getBatchDetail(String batchId) {
        return catalogService.getBatchDetail(batchId);
    }

    public Map<String, Integer> archiveAtoms(List<String> atomIds) {
        return lifecycleService.archiveAtoms(atomIds);
    }

    public Map<String, Integer> archiveAtoms(List<String> atomIds, QuestionBankImportScope scope) {
        return lifecycleService.archiveAtoms(atomIds, scope);
    }

    public Map<String, Integer> publishAtoms(List<String> atomIds) {
        return lifecycleService.publishAtoms(atomIds);
    }

    public Map<String, Integer> publishAtoms(List<String> atomIds, QuestionBankImportScope scope) {
        return lifecycleService.publishAtoms(atomIds, scope);
    }

    public Map<String, Integer> publishAllDrafts(QuestionBankImportScope scope) {
        return lifecycleService.publishAllDrafts(scope);
    }

    public Map<String, Integer> archiveAll(QuestionBankImportScope scope) {
        return lifecycleService.archiveAll(scope);
    }

    public Map<String, Integer> archiveBatch(String batchId) {
        return lifecycleService.archiveBatch(batchId);
    }

    public Map<String, Integer> reindexAtoms(List<String> atomIds) {
        return vectorSyncService.reindexAtoms(atomIds);
    }

    public Map<String, Integer> reindexAtoms(List<String> atomIds, QuestionBankImportScope scope) {
        return vectorSyncService.reindexAtoms(atomIds, scope);
    }

    public Map<String, Integer> reindexUnsyncedPublishedAtomResult() {
        return vectorSyncService.reindexUnsyncedPublishedAtomResult();
    }

    public Map<String, Integer> reindexAllPublishedAtomResult() {
        return vectorSyncService.reindexAllPublishedAtomResult();
    }

    public QuestionBankImportResult importBatch(QuestionBankImportRequest request) {
        return importService.importBatch(request);
    }

    public QuestionBankImportResult importBatch(QuestionBankImportRequest request, QuestionBankImportScope scope) {
        return importService.importBatch(request, scope);
    }

    public int reindexPublishedAtoms() {
        return vectorSyncService.reindexPublishedAtoms();
    }

    public int reindexUnsyncedPublishedAtoms() {
        return vectorSyncService.reindexUnsyncedPublishedAtoms();
    }

    public boolean syncAtom(KnowledgeAtom atom) {
        return vectorSyncService.syncAtom(atom);
    }

    public String buildPromptContext(KnowledgeAtom atom) {
        return searchService.buildPromptContext(atom);
    }
}

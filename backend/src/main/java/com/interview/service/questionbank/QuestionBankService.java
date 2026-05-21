package com.interview.service.questionbank;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.interview.config.PositionCategoryConfig;
import com.interview.dto.questionbank.KnowledgeAtomPayload;
import com.interview.dto.questionbank.QuestionBankAtomListItem;
import com.interview.dto.questionbank.QuestionBankAtomQueryRequest;
import com.interview.dto.questionbank.QuestionBankBatchDetailResponse;
import com.interview.dto.questionbank.QuestionBankBatchListItem;
import com.interview.dto.questionbank.QuestionBankImportPreviewResponse;
import com.interview.dto.questionbank.QuestionBankImportRequest;
import com.interview.dto.questionbank.QuestionBankImportResult;
import com.interview.dto.questionbank.QuestionBankPageResponse;
import com.interview.dto.questionbank.QuestionBankSearchRequest;
import com.interview.dto.questionbank.QuestionBankSearchResult;
import com.interview.entity.KnowledgeAtom;
import com.interview.entity.KnowledgeAtomImportBatch;
import com.interview.entity.KnowledgeAtomVersion;
import com.interview.mapper.KnowledgeAtomImportBatchMapper;
import com.interview.mapper.KnowledgeAtomMapper;
import com.interview.mapper.KnowledgeAtomVersionMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
public class QuestionBankService {

    public static final String STATUS_DRAFT = "DRAFT";
    public static final String STATUS_PUBLISHED = "PUBLISHED";
    public static final String STATUS_ARCHIVED = "ARCHIVED";

    private final KnowledgeAtomMapper atomMapper;
    private final KnowledgeAtomVersionMapper versionMapper;
    private final KnowledgeAtomImportBatchMapper batchMapper;
    private final PositionCategoryConfig categoryConfig;
    private final QdrantVectorService qdrantVectorService;

    public QuestionBankService(KnowledgeAtomMapper atomMapper,
                               KnowledgeAtomVersionMapper versionMapper,
                               KnowledgeAtomImportBatchMapper batchMapper,
                               PositionCategoryConfig categoryConfig,
                               QdrantVectorService qdrantVectorService) {
        this.atomMapper = atomMapper;
        this.versionMapper = versionMapper;
        this.batchMapper = batchMapper;
        this.categoryConfig = categoryConfig;
        this.qdrantVectorService = qdrantVectorService;
    }

    public KnowledgeAtom getByAtomId(String atomId) {
        if (atomId == null || atomId.isBlank()) return null;
        return atomMapper.selectOne(new QueryWrapper<KnowledgeAtom>().eq("atom_id", atomId).last("LIMIT 1"));
    }

    public List<QuestionBankSearchResult> search(QuestionBankSearchRequest request) {
        int limit = request.getLimit() > 0 ? Math.min(request.getLimit(), 10) : 3;
        String query = request.getQuery() != null ? request.getQuery().trim() : "";
        if (query.length() <= 2) return List.of();

        List<String> categories = normalizeCategories(request);
        List<String> exclude = request.getExcludeAtomIds() != null ? request.getExcludeAtomIds() : List.of();

        List<QdrantVectorService.VectorHit> hits =
                qdrantVectorService.search(query, categories, exclude, limit);
        List<QuestionBankSearchResult> results = loadHits(hits);
        if (!results.isEmpty()) {
            return results.stream().limit(limit).collect(Collectors.toList());
        }
        return fallbackSearch(query, categories, exclude, limit);
    }

    public List<Map<String, Object>> listCategories() {
        QueryWrapper<KnowledgeAtom> query = new QueryWrapper<>();
        query.select("category", "status", "COUNT(*) AS count")
                .groupBy("category", "status");
        List<Map<String, Object>> rows = atomMapper.selectMaps(query);
        Map<String, Map<String, Object>> grouped = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            String category = String.valueOf(row.get("category"));
            String status = String.valueOf(row.get("status"));
            int count = ((Number) row.get("count")).intValue();
            Map<String, Object> item = grouped.computeIfAbsent(category, k -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("category", k);
                m.put("total", 0);
                m.put("published", 0);
                m.put("draft", 0);
                m.put("archived", 0);
                return m;
            });
            item.put("total", ((Number) item.get("total")).intValue() + count);
            if (STATUS_PUBLISHED.equalsIgnoreCase(status)) item.put("published", count);
            if (STATUS_DRAFT.equalsIgnoreCase(status)) item.put("draft", count);
            if (STATUS_ARCHIVED.equalsIgnoreCase(status)) item.put("archived", count);
        }
        return new ArrayList<>(grouped.values());
    }

    public List<String> validateImportPackage(QuestionBankImportRequest request) {
        return validateImport(request);
    }

    public QuestionBankImportPreviewResponse previewImport(QuestionBankImportRequest request) {
        QuestionBankImportPreviewResponse response = new QuestionBankImportPreviewResponse();
        String batchId = nonBlank(request.getBatchId(), "qb-" + UUID.randomUUID());
        response.setBatchId(batchId);
        response.setMode(normalizeMode(request.getMode()));
        response.setTargetCategory(request.getTargetCategory());
        response.setSourceRef(request.getSourceRef());
        response.setReceived(request.getAtoms() != null ? request.getAtoms().size() : 0);
        response.setErrors(validateImport(request));
        response.setBatchIdExists(batchExists(batchId));

        Map<String, Integer> seen = new LinkedHashMap<>();
        if (request.getAtoms() != null) {
            for (KnowledgeAtomPayload atom : request.getAtoms()) {
                if (!isBlank(atom.getId())) {
                    seen.merge(atom.getId().trim(), 1, Integer::sum);
                }
            }
        }
        seen.forEach((id, count) -> {
            if (count > 1) response.getDuplicateAtomIds().add(id);
        });

        List<String> atomIds = new ArrayList<>(seen.keySet());
        Set<String> existingIds = new LinkedHashSet<>();
        if (!atomIds.isEmpty()) {
            existingIds.addAll(atomMapper.selectList(new QueryWrapper<KnowledgeAtom>()
                            .in("atom_id", atomIds))
                    .stream()
                    .map(KnowledgeAtom::getAtomId)
                    .collect(Collectors.toCollection(LinkedHashSet::new)));
        }
        for (String atomId : atomIds) {
            if (existingIds.contains(atomId)) {
                response.getUpdateAtomIds().add(atomId);
            } else {
                response.getNewAtomIds().add(atomId);
            }
        }
        response.setNewCount(response.getNewAtomIds().size());
        response.setUpdateCount(response.getUpdateAtomIds().size());
        return response;
    }

    public QuestionBankPageResponse<QuestionBankAtomListItem> listAtoms(QuestionBankAtomQueryRequest request) {
        int page = Math.max(1, request.getPage());
        int size = Math.min(Math.max(1, request.getSize()), 100);
        List<String> batchAtomIds = !isBlank(request.getBatchId())
                ? batchAtomIds(request.getBatchId(), false)
                : List.of();

        QueryWrapper<KnowledgeAtom> countWrapper = buildAtomQuery(request, batchAtomIds);
        long total = safeLong(atomMapper.selectCount(countWrapper));
        if (total == 0) {
            return QuestionBankPageResponse.of(0, page, size, List.of());
        }

        int offset = (page - 1) * size;
        QueryWrapper<KnowledgeAtom> listWrapper = buildAtomQuery(request, batchAtomIds)
                .orderByDesc("update_time")
                .last("LIMIT " + offset + ", " + size);
        List<QuestionBankAtomListItem> items = atomMapper.selectList(listWrapper).stream()
                .map(this::toListItem)
                .collect(Collectors.toList());
        return QuestionBankPageResponse.of(total, page, size, items);
    }

    public QuestionBankPageResponse<QuestionBankBatchListItem> listBatches(int pageValue, int sizeValue) {
        int page = Math.max(1, pageValue);
        int size = Math.min(Math.max(1, sizeValue), 100);
        long total = safeLong(batchMapper.selectCount(new QueryWrapper<>()));
        if (total == 0) {
            return QuestionBankPageResponse.of(0, page, size, List.of());
        }
        int offset = (page - 1) * size;
        List<QuestionBankBatchListItem> items = batchMapper.selectList(new QueryWrapper<KnowledgeAtomImportBatch>()
                        .orderByDesc("create_time")
                        .last("LIMIT " + offset + ", " + size))
                .stream()
                .map(this::toBatchListItem)
                .collect(Collectors.toList());
        return QuestionBankPageResponse.of(total, page, size, items);
    }

    public QuestionBankBatchDetailResponse getBatchDetail(String batchId) {
        QuestionBankBatchDetailResponse response = new QuestionBankBatchDetailResponse();
        response.setBatch(batchMapper.selectOne(new QueryWrapper<KnowledgeAtomImportBatch>()
                .eq("batch_id", batchId)
                .last("LIMIT 1")));

        List<String> atomIds = batchAtomIds(batchId, false);
        List<String> latestLinkedIds = batchAtomIds(batchId, true);
        response.setAtomCount(atomIds.size());
        response.setLatestLinkedCount(latestLinkedIds.size());
        if (atomIds.isEmpty()) {
            return response;
        }
        Map<String, KnowledgeAtom> byId = atomMapper.selectList(new QueryWrapper<KnowledgeAtom>()
                        .in("atom_id", atomIds))
                .stream()
                .collect(Collectors.toMap(KnowledgeAtom::getAtomId, atom -> atom));
        response.setAtoms(atomIds.stream()
                .map(byId::get)
                .filter(atom -> atom != null)
                .map(this::toListItem)
                .collect(Collectors.toList()));
        return response;
    }

    @Transactional
    public Map<String, Integer> archiveAtoms(List<String> atomIds) {
        List<String> ids = cleanAtomIds(atomIds);
        if (ids.isEmpty()) return resultMap("matched", 0, "archived", 0, "deleted", 0, "skipped", 0);

        List<KnowledgeAtom> atoms = atomMapper.selectList(new QueryWrapper<KnowledgeAtom>().in("atom_id", ids));
        int archived = 0;
        int deleted = 0;
        int skipped = 0;
        for (KnowledgeAtom atom : atoms) {
            if (STATUS_ARCHIVED.equalsIgnoreCase(atom.getStatus())) {
                skipped++;
                continue;
            }
            atom.setStatus(STATUS_ARCHIVED);
            atom.setVectorStatus("SKIPPED");
            atomMapper.updateById(atom);
            recordVersion(atom, "archive:admin");
            if (qdrantVectorService.delete(atom.getAtomId())) deleted++;
            archived++;
        }
        return resultMap("matched", atoms.size(), "archived", archived, "deleted", deleted, "skipped", skipped);
    }

    @Transactional
    public Map<String, Integer> publishAtoms(List<String> atomIds) {
        List<String> ids = cleanAtomIds(atomIds);
        if (ids.isEmpty()) return resultMap("matched", 0, "published", 0, "synced", 0, "failed", 0);

        List<KnowledgeAtom> atoms = atomMapper.selectList(new QueryWrapper<KnowledgeAtom>().in("atom_id", ids));
        int published = 0;
        int synced = 0;
        int failed = 0;
        for (KnowledgeAtom atom : atoms) {
            atom.setStatus(STATUS_PUBLISHED);
            atom.setVectorStatus("PENDING");
            atomMapper.updateById(atom);
            recordVersion(atom, "publish:admin");
            published++;
            if (syncAtom(atom)) {
                synced++;
            } else {
                failed++;
            }
        }
        return resultMap("matched", atoms.size(), "published", published, "synced", synced, "failed", failed);
    }

    @Transactional
    public Map<String, Integer> archiveBatch(String batchId) {
        return archiveAtoms(batchAtomIds(batchId, true));
    }

    public Map<String, Integer> reindexAtoms(List<String> atomIds) {
        List<String> ids = cleanAtomIds(atomIds);
        if (ids.isEmpty()) return resultMap("matched", 0, "synced", 0, "failed", 0, "skipped", 0);

        List<KnowledgeAtom> atoms = atomMapper.selectList(new QueryWrapper<KnowledgeAtom>().in("atom_id", ids));
        int synced = 0;
        int failed = 0;
        int skipped = 0;
        for (KnowledgeAtom atom : atoms) {
            if (!STATUS_PUBLISHED.equalsIgnoreCase(atom.getStatus())) {
                skipped++;
                continue;
            }
            if (syncAtom(atom)) {
                synced++;
            } else {
                failed++;
            }
        }
        return resultMap("matched", atoms.size(), "synced", synced, "failed", failed, "skipped", skipped);
    }

    public Map<String, Integer> reindexUnsyncedPublishedAtomResult() {
        List<KnowledgeAtom> atoms = atomMapper.selectList(new QueryWrapper<KnowledgeAtom>()
                .eq("status", STATUS_PUBLISHED)
                .ne("vector_status", "SYNCED"));
        int synced = 0;
        int failed = 0;
        for (KnowledgeAtom atom : atoms) {
            if (syncAtom(atom)) synced++;
            else failed++;
        }
        return resultMap("matched", atoms.size(), "synced", synced, "failed", failed);
    }

    public Map<String, Integer> reindexAllPublishedAtomResult() {
        List<KnowledgeAtom> atoms = atomMapper.selectList(new QueryWrapper<KnowledgeAtom>()
                .eq("status", STATUS_PUBLISHED));
        int synced = 0;
        int failed = 0;
        for (KnowledgeAtom atom : atoms) {
            if (syncAtom(atom)) synced++;
            else failed++;
        }
        return resultMap("matched", atoms.size(), "synced", synced, "failed", failed);
    }

    @Transactional
    public QuestionBankImportResult importBatch(QuestionBankImportRequest request) {
        String mode = normalizeMode(request.getMode());
        List<String> errors = validateImport(request);
        String batchId = request.getBatchId();
        if (batchId == null || batchId.isBlank()) {
            batchId = "qb-" + UUID.randomUUID();
        }
        int atomCount = request.getAtoms() != null ? request.getAtoms().size() : 0;

        if (!errors.isEmpty()) {
            return QuestionBankImportResult.builder()
                    .batchId(batchId)
                    .mode(mode)
                    .received(atomCount)
                    .failed(atomCount)
                    .errors(errors)
                    .build();
        }
        if ("DRY_RUN".equals(mode)) {
            return QuestionBankImportResult.builder()
                    .batchId(batchId)
                    .mode(mode)
                    .received(atomCount)
                    .imported(0)
                    .published(0)
                    .failed(0)
                    .build();
        }

        batchId = uniqueBatchId(batchId);
        KnowledgeAtomImportBatch batch = new KnowledgeAtomImportBatch();
        batch.setBatchId(batchId);
        batch.setSourceRef(request.getSourceRef());
        batch.setTargetCategory(request.getTargetCategory());
        batch.setMode(mode);
        batch.setAtomCount(atomCount);
        batch.setValidationReport(JSON.toJSONString(request.getValidationReport()));
        batch.setReviewReport(JSON.toJSONString(request.getReviewReport()));
        batch.setStatus("CREATED");
        batchMapper.insert(batch);

        int imported = 0;
        int published = 0;
        int failed = 0;
        for (KnowledgeAtomPayload payload : request.getAtoms()) {
            try {
                KnowledgeAtom atom = toAtom(payload, request.getTargetCategory(), request.getSourceRef(), mode);
                upsertAtom(atom, "import:" + batchId);
                imported++;
                if (STATUS_PUBLISHED.equals(atom.getStatus())) {
                    if (syncAtom(atom)) published++;
                }
            } catch (Exception e) {
                failed++;
                errors.add(payload.getId() + ": " + e.getMessage());
            }
        }
        batch.setStatus(failed == 0 ? "IMPORTED" : "FAILED");
        batch.setValidationReport(JSON.toJSONString(Map.of("errors", errors)));
        batchMapper.updateById(batch);
        return QuestionBankImportResult.builder()
                .batchId(batchId)
                .mode(mode)
                .received(batch.getAtomCount())
                .imported(imported)
                .published(published)
                .failed(failed)
                .errors(errors)
                .build();
    }

    public int reindexPublishedAtoms() {
        List<KnowledgeAtom> atoms = atomMapper.selectList(new QueryWrapper<KnowledgeAtom>()
                .eq("status", STATUS_PUBLISHED));
        int synced = 0;
        for (KnowledgeAtom atom : atoms) {
            if (syncAtom(atom)) synced++;
        }
        return synced;
    }

    public int reindexUnsyncedPublishedAtoms() {
        List<KnowledgeAtom> atoms = atomMapper.selectList(new QueryWrapper<KnowledgeAtom>()
                .eq("status", STATUS_PUBLISHED)
                .ne("vector_status", "SYNCED"));
        int synced = 0;
        for (KnowledgeAtom atom : atoms) {
            if (syncAtom(atom)) synced++;
        }
        return synced;
    }

    public boolean syncAtom(KnowledgeAtom atom) {
        boolean ok = qdrantVectorService.upsert(atom);
        atom.setVectorStatus(ok ? "SYNCED" : "FAILED");
        atom.setLastIndexedAt(ok ? LocalDateTime.now() : atom.getLastIndexedAt());
        atomMapper.updateById(atom);
        return ok;
    }

    private QueryWrapper<KnowledgeAtom> buildAtomQuery(QuestionBankAtomQueryRequest request, List<String> batchAtomIds) {
        QueryWrapper<KnowledgeAtom> wrapper = new QueryWrapper<>();
        if (!isBlank(request.getKeyword())) {
            String keyword = request.getKeyword().trim();
            wrapper.and(w -> w.like("atom_id", keyword)
                    .or().like("subject", keyword)
                    .or().like("principles", keyword)
                    .or().like("tags_json", keyword));
        }
        if (!isBlank(request.getCategory())) wrapper.eq("category", request.getCategory().trim());
        if (!isBlank(request.getStatus())) wrapper.eq("status", request.getStatus().trim().toUpperCase());
        if (!isBlank(request.getDifficulty())) wrapper.eq("difficulty", request.getDifficulty().trim());
        if (!isBlank(request.getVectorStatus())) wrapper.eq("vector_status", request.getVectorStatus().trim().toUpperCase());
        if (!isBlank(request.getSourceRef())) wrapper.like("source_ref", request.getSourceRef().trim());
        if (!isBlank(request.getBatchId())) {
            if (batchAtomIds.isEmpty()) {
                wrapper.eq("atom_id", "__NO_BATCH_ATOMS__");
            } else {
                wrapper.in("atom_id", batchAtomIds);
            }
        }
        return wrapper;
    }

    private List<String> batchAtomIds(String batchId, boolean latestOnly) {
        if (isBlank(batchId)) return List.of();
        String reason = "import:" + batchId.trim();
        List<String> ids = versionMapper.selectList(new QueryWrapper<KnowledgeAtomVersion>()
                        .eq("change_reason", reason)
                        .orderByAsc("id"))
                .stream()
                .map(KnowledgeAtomVersion::getAtomId)
                .filter(id -> !isBlank(id))
                .collect(Collectors.toCollection(LinkedHashSet::new))
                .stream()
                .collect(Collectors.toList());
        if (!latestOnly || ids.isEmpty()) return ids;
        return ids.stream()
                .filter(atomId -> isLatestVersionFromBatch(atomId, reason))
                .collect(Collectors.toList());
    }

    private boolean isLatestVersionFromBatch(String atomId, String reason) {
        KnowledgeAtomVersion latest = versionMapper.selectOne(new QueryWrapper<KnowledgeAtomVersion>()
                .eq("atom_id", atomId)
                .orderByDesc("version_no")
                .last("LIMIT 1"));
        return latest != null && reason.equals(latest.getChangeReason());
    }

    private QuestionBankAtomListItem toListItem(KnowledgeAtom atom) {
        QuestionBankAtomListItem item = new QuestionBankAtomListItem();
        item.setAtomId(atom.getAtomId());
        item.setSubject(atom.getSubject());
        item.setCategory(atom.getCategory());
        item.setDifficulty(atom.getDifficulty());
        item.setStatus(atom.getStatus());
        item.setVectorStatus(atom.getVectorStatus());
        item.setSourceRef(atom.getSourceRef());
        item.setLastIndexedAt(atom.getLastIndexedAt());
        item.setUpdateTime(atom.getUpdateTime());
        return item;
    }

    private QuestionBankBatchListItem toBatchListItem(KnowledgeAtomImportBatch batch) {
        QuestionBankBatchListItem item = new QuestionBankBatchListItem();
        item.setBatchId(batch.getBatchId());
        item.setSourceRef(batch.getSourceRef());
        item.setTargetCategory(batch.getTargetCategory());
        item.setMode(batch.getMode());
        item.setStatus(batch.getStatus());
        item.setAtomCount(batch.getAtomCount());
        item.setCreateTime(batch.getCreateTime());
        item.setUpdateTime(batch.getUpdateTime());
        return item;
    }

    private List<String> cleanAtomIds(List<String> atomIds) {
        if (atomIds == null || atomIds.isEmpty()) return List.of();
        return atomIds.stream()
                .filter(id -> !isBlank(id))
                .map(String::trim)
                .collect(Collectors.toCollection(LinkedHashSet::new))
                .stream()
                .collect(Collectors.toList());
    }

    private Map<String, Integer> resultMap(Object... values) {
        Map<String, Integer> result = new LinkedHashMap<>();
        for (int i = 0; i + 1 < values.length; i += 2) {
            result.put(String.valueOf(values[i]), (Integer) values[i + 1]);
        }
        return result;
    }

    private String uniqueBatchId(String batchId) {
        if (!batchExists(batchId)) return batchId;
        return batchId + "-" + System.currentTimeMillis();
    }

    private boolean batchExists(String batchId) {
        if (isBlank(batchId)) return false;
        return safeLong(batchMapper.selectCount(new QueryWrapper<KnowledgeAtomImportBatch>()
                .eq("batch_id", batchId))) > 0;
    }

    private long safeLong(Long value) {
        return value == null ? 0 : value;
    }

    private void upsertAtom(KnowledgeAtom atom, String reason) {
        KnowledgeAtom existing = getByAtomId(atom.getAtomId());
        if (existing != null) {
            atom.setId(existing.getId());
            atom.setCreateTime(existing.getCreateTime());
            atomMapper.updateById(atom);
        } else {
            atomMapper.insert(atom);
        }
        recordVersion(atom, reason);
    }

    private void recordVersion(KnowledgeAtom atom, String reason) {
        Long count = versionMapper.selectCount(new QueryWrapper<KnowledgeAtomVersion>()
                .eq("atom_id", atom.getAtomId()));
        KnowledgeAtomVersion version = new KnowledgeAtomVersion();
        version.setAtomId(atom.getAtomId());
        version.setVersionNo((count == null ? 0 : count.intValue()) + 1);
        version.setSnapshotJson(JSON.toJSONString(atom));
        version.setChangeReason(reason);
        versionMapper.insert(version);
    }

    private KnowledgeAtom toAtom(KnowledgeAtomPayload payload, String defaultCategory, String sourceRef, String mode) {
        KnowledgeAtom atom = new KnowledgeAtom();
        atom.setAtomId(payload.getId().trim());
        atom.setSubject(payload.getSubject().trim());
        atom.setCategory(nonBlank(payload.getCategory(), defaultCategory));
        atom.setDifficulty(payload.getDifficulty());
        atom.setTagsJson(JSON.toJSONString(payload.getTags() != null ? payload.getTags() : List.of()));
        KnowledgeAtomPayload.Content content = payload.getContent() != null ? payload.getContent() : new KnowledgeAtomPayload.Content();
        atom.setPrinciples(content.getPrinciples());
        atom.setPitfalls(content.getPitfalls());
        atom.setFollowUpPathsJson(JSON.toJSONString(content.getFollowUpPaths() != null ? content.getFollowUpPaths() : List.of()));
        atom.setStatus("AUTO_PUBLISH".equals(mode) ? STATUS_PUBLISHED : STATUS_DRAFT);
        atom.setSourceRef(nonBlank(payload.getSourceRef(), sourceRef));
        atom.setChecksum(checksum(atom));
        atom.setVectorStatus(STATUS_PUBLISHED.equals(atom.getStatus()) ? "PENDING" : "SKIPPED");
        return atom;
    }

    private String normalizeMode(String value) {
        if (value == null || value.isBlank()) return "DRAFT";
        String mode = value.trim().toUpperCase();
        if (List.of("DRY_RUN", "DRAFT", "AUTO_PUBLISH").contains(mode)) return mode;
        return "DRAFT";
    }

    private List<String> validateImport(QuestionBankImportRequest request) {
        List<String> errors = new ArrayList<>();
        if (request.getAtoms() == null || request.getAtoms().isEmpty()) {
            errors.add("atoms must not be empty");
            return errors;
        }
        Map<String, Integer> seen = new HashMap<>();
        for (KnowledgeAtomPayload atom : request.getAtoms()) {
            if (isBlank(atom.getId())) errors.add("atom id is required");
            if (isBlank(atom.getSubject())) errors.add(atom.getId() + ": subject is required");
            String category = nonBlank(atom.getCategory(), request.getTargetCategory());
            if (isBlank(category)) errors.add(atom.getId() + ": category is required");
            if (atom.getContent() == null || isBlank(atom.getContent().getPrinciples())) {
                errors.add(atom.getId() + ": content.principles is required");
            }
            if (!isBlank(atom.getId())) seen.merge(atom.getId(), 1, Integer::sum);
        }
        seen.forEach((id, count) -> {
            if (count > 1) errors.add("duplicate atom id in package: " + id);
        });
        return errors;
    }

    private List<String> normalizeCategories(QuestionBankSearchRequest request) {
        if (request.getCategories() != null && !request.getCategories().isEmpty()) {
            return request.getCategories();
        }
        if (!isBlank(request.getPosition())) {
            return categoryConfig.getCategoriesFor(request.getPosition());
        }
        return List.of();
    }

    private List<QuestionBankSearchResult> loadHits(List<QdrantVectorService.VectorHit> hits) {
        if (hits == null || hits.isEmpty()) return List.of();
        List<String> atomIds = hits.stream().map(QdrantVectorService.VectorHit::getAtomId).collect(Collectors.toList());
        List<KnowledgeAtom> atoms = atomMapper.selectList(new QueryWrapper<KnowledgeAtom>()
                .in("atom_id", atomIds)
                .eq("status", STATUS_PUBLISHED));
        Map<String, KnowledgeAtom> byId = atoms.stream().collect(Collectors.toMap(KnowledgeAtom::getAtomId, a -> a));
        return hits.stream()
                .filter(hit -> byId.containsKey(hit.getAtomId()))
                .map(hit -> toResult(byId.get(hit.getAtomId()), hit.getScore()))
                .collect(Collectors.toList());
    }

    private List<QuestionBankSearchResult> fallbackSearch(String query, List<String> categories, List<String> exclude, int limit) {
        QueryWrapper<KnowledgeAtom> wrapper = new QueryWrapper<>();
        wrapper.eq("status", STATUS_PUBLISHED)
                .in(categories != null && !categories.isEmpty(), "category", categories)
                .notIn(exclude != null && !exclude.isEmpty(), "atom_id", exclude)
                .and(w -> w.like("subject", query)
                        .or().like("principles", query)
                        .or().like("tags_json", query))
                .orderByDesc("update_time")
                .last("LIMIT " + Math.max(1, limit));
        return atomMapper.selectList(wrapper).stream()
                .sorted(Comparator.comparing(KnowledgeAtom::getUpdateTime, Comparator.nullsLast(Comparator.reverseOrder())))
                .map(atom -> toResult(atom, 0.0))
                .collect(Collectors.toList());
    }

    private QuestionBankSearchResult toResult(KnowledgeAtom atom, double score) {
        return QuestionBankSearchResult.builder()
                .atomId(atom.getAtomId())
                .subject(atom.getSubject())
                .category(atom.getCategory())
                .difficulty(atom.getDifficulty())
                .score(score)
                .promptContext(buildPromptContext(atom))
                .atom(atom)
                .build();
    }

    public String buildPromptContext(KnowledgeAtom atom) {
        return "考核点: " + atom.getSubject() + "\n"
                + "核心原理与标准答案: " + atom.getPrinciples() + "\n"
                + (isBlank(atom.getPitfalls()) ? "" : "面试常见陷阱与候选人易错点: " + atom.getPitfalls() + "\n")
                + (isBlank(atom.getFollowUpPathsJson()) ? "" : "推荐的深度追问路径: " + atom.getFollowUpPathsJson() + "\n");
    }

    private String checksum(KnowledgeAtom atom) {
        String raw = String.join("|",
                atom.getAtomId(), atom.getSubject(), atom.getCategory(),
                String.valueOf(atom.getDifficulty()), String.valueOf(atom.getTagsJson()),
                atom.getPrinciples(), String.valueOf(atom.getPitfalls()),
                String.valueOf(atom.getFollowUpPathsJson()));
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return Integer.toHexString(raw.hashCode());
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String nonBlank(String first, String fallback) {
        return !isBlank(first) ? first : fallback;
    }
}

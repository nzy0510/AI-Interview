package com.interview.service.questionbank;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.interview.dto.questionbank.KnowledgeAtomPayload;
import com.interview.dto.questionbank.QuestionBankAtomListItem;
import com.interview.dto.questionbank.QuestionBankAtomQueryRequest;
import com.interview.dto.questionbank.QuestionBankBatchListItem;
import com.interview.dto.questionbank.QuestionBankImportRequest;
import com.interview.entity.KnowledgeAtom;
import com.interview.entity.KnowledgeAtomImportBatch;
import com.interview.entity.KnowledgeAtomVersion;
import com.interview.mapper.KnowledgeAtomImportBatchMapper;
import com.interview.mapper.KnowledgeAtomMapper;
import com.interview.mapper.KnowledgeAtomVersionMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

abstract class QuestionBankSupport {

    protected final Logger log = LoggerFactory.getLogger(getClass());
    protected final KnowledgeAtomMapper atomMapper;
    protected final KnowledgeAtomVersionMapper versionMapper;
    protected final KnowledgeAtomImportBatchMapper batchMapper;

    QuestionBankSupport(KnowledgeAtomMapper atomMapper,
                        KnowledgeAtomVersionMapper versionMapper,
                        KnowledgeAtomImportBatchMapper batchMapper) {
        this.atomMapper = atomMapper;
        this.versionMapper = versionMapper;
        this.batchMapper = batchMapper;
    }

    KnowledgeAtom getByAtomId(String atomId) {
        if (atomId == null || atomId.isBlank()) return null;
        return atomMapper.selectOne(new QueryWrapper<KnowledgeAtom>().eq("atom_id", atomId).last("LIMIT 1"));
    }

    protected QueryWrapper<KnowledgeAtom> buildAtomQuery(QuestionBankAtomQueryRequest request, List<String> batchAtomIds) {
        return buildAtomQuery(request, batchAtomIds, null);
    }

    protected QueryWrapper<KnowledgeAtom> buildAtomQuery(QuestionBankAtomQueryRequest request,
                                                         List<String> batchAtomIds,
                                                         QuestionBankImportScope scope) {
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
        applyScope(wrapper, scope);
        return wrapper;
    }

    protected List<String> batchAtomIds(String batchId, boolean latestOnly) {
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

    protected QuestionBankAtomListItem toListItem(KnowledgeAtom atom) {
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

    protected QuestionBankBatchListItem toBatchListItem(KnowledgeAtomImportBatch batch) {
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

    protected List<String> cleanAtomIds(List<String> atomIds) {
        if (atomIds == null || atomIds.isEmpty()) return List.of();
        return atomIds.stream()
                .filter(id -> !isBlank(id))
                .map(String::trim)
                .collect(Collectors.toCollection(LinkedHashSet::new))
                .stream()
                .collect(Collectors.toList());
    }

    protected Map<String, Integer> resultMap(Object... values) {
        Map<String, Integer> result = new LinkedHashMap<>();
        for (int i = 0; i + 1 < values.length; i += 2) {
            result.put(String.valueOf(values[i]), (Integer) values[i + 1]);
        }
        return result;
    }

    protected String uniqueBatchId(String batchId) {
        if (!batchExists(batchId)) return batchId;
        return batchId + "-" + System.currentTimeMillis();
    }

    protected boolean batchExists(String batchId) {
        if (isBlank(batchId)) return false;
        return safeLong(batchMapper.selectCount(new QueryWrapper<KnowledgeAtomImportBatch>()
                .eq("batch_id", batchId))) > 0;
    }

    protected long safeLong(Long value) {
        return value == null ? 0 : value;
    }

    protected void upsertAtom(KnowledgeAtom atom, String reason, QuestionBankImportScope scope) {
        KnowledgeAtom existing = getByAtomId(atom.getAtomId());
        if (existing != null) {
            if (scope != null && !matchesScope(existing, scope)) {
                throw new IllegalStateException("atom id conflicts outside current knowledge base: " + atom.getAtomId());
            }
            atom.setId(existing.getId());
            atom.setCreateTime(existing.getCreateTime());
            atomMapper.updateById(atom);
        } else {
            atomMapper.insert(atom);
        }
        recordVersion(atom, reason);
    }

    protected void recordVersion(KnowledgeAtom atom, String reason) {
        for (int attempt = 0; attempt < 3; attempt++) {
            Long count = versionMapper.selectCount(new QueryWrapper<KnowledgeAtomVersion>()
                    .eq("atom_id", atom.getAtomId()));
            KnowledgeAtomVersion version = new KnowledgeAtomVersion();
            version.setAtomId(atom.getAtomId());
            version.setVersionNo((count == null ? 0 : count.intValue()) + 1);
            version.setSnapshotJson(JSON.toJSONString(atom));
            version.setChangeReason(reason);
            try {
                versionMapper.insert(version);
                return;
            } catch (DuplicateKeyException e) {
                if (attempt == 2) throw e;
                log.debug("Knowledge atom version raced, retrying: atomId={}, version={}",
                        atom.getAtomId(), version.getVersionNo());
            }
        }
    }

    protected KnowledgeAtom toAtom(KnowledgeAtomPayload payload,
                                   String defaultCategory,
                                   String sourceRef,
                                   String mode,
                                   QuestionBankImportScope scope) {
        KnowledgeAtom atom = new KnowledgeAtom();
        atom.setAtomId(scopedAtomId(payload.getId(), scope));
        atom.setSubject(payload.getSubject().trim());
        atom.setCategory(nonBlank(payload.getCategory(), defaultCategory));
        atom.setDifficulty(payload.getDifficulty());
        atom.setTagsJson(JSON.toJSONString(payload.getTags() != null ? payload.getTags() : List.of()));
        KnowledgeAtomPayload.Content content = payload.getContent() != null ? payload.getContent() : new KnowledgeAtomPayload.Content();
        atom.setPrinciples(content.getPrinciples());
        atom.setPitfalls(content.getPitfalls());
        atom.setFollowUpPathsJson(JSON.toJSONString(content.getFollowUpPaths() != null ? content.getFollowUpPaths() : List.of()));
        atom.setStatus("AUTO_PUBLISH".equals(mode) ? QuestionBankService.STATUS_PUBLISHED : QuestionBankService.STATUS_DRAFT);
        atom.setSourceRef(nonBlank(payload.getSourceRef(), sourceRef));
        atom.setChecksum(checksum(atom));
        atom.setVectorStatus(QuestionBankService.STATUS_PUBLISHED.equals(atom.getStatus()) ? "PENDING" : "SKIPPED");
        if (scope != null) {
            atom.setScope(scope.scope());
            atom.setOwnerUserId(scope.ownerUserId());
            atom.setPositionId(scope.positionId());
            atom.setKnowledgeBaseId(scope.knowledgeBaseId());
            atom.setReviewStatus("PASS");
            atom.setReviewReason("导入包人工维护");
            atom.setReviewConfidence(1.0);
            atom.setReviewedBy(scope.currentUserId());
            atom.setReviewedAt(LocalDateTime.now());
            atom.setPublicationStatus(QuestionBankService.STATUS_PUBLISHED.equals(atom.getStatus())
                    ? QuestionBankService.STATUS_PUBLISHED : QuestionBankService.STATUS_DRAFT);
            if (QuestionBankService.STATUS_PUBLISHED.equals(atom.getStatus())) {
                atom.setPublishedBy(scope.currentUserId());
                atom.setPublishedAt(LocalDateTime.now());
            }
        }
        return atom;
    }

    protected String normalizeMode(String value, QuestionBankImportScope scope) {
        if (value == null || value.isBlank()) return "DRAFT";
        String mode = value.trim().toUpperCase();
        if (scope != null && "AUTO_PUBLISH".equals(mode) && !scope.allowAutoPublish()) {
            return "DRAFT";
        }
        if (List.of("DRY_RUN", "DRAFT", "AUTO_PUBLISH").contains(mode)) return mode;
        return "DRAFT";
    }

    protected QueryWrapper<KnowledgeAtom> applyScope(QueryWrapper<KnowledgeAtom> wrapper, QuestionBankImportScope scope) {
        if (scope == null) {
            return wrapper;
        }
        wrapper.eq("scope", scope.scope())
                .eq(scope.ownerUserId() != null, "owner_user_id", scope.ownerUserId())
                .eq("position_id", scope.positionId())
                .eq("knowledge_base_id", scope.knowledgeBaseId());
        return wrapper;
    }

    protected String scopedAtomId(String atomId, QuestionBankImportScope scope) {
        String cleaned = atomId == null ? "" : atomId.trim();
        if (!isPrivateScope(scope)) {
            return cleaned;
        }
        String prefix = "kb" + scope.knowledgeBaseId() + "-";
        String candidate = prefix + cleaned;
        if (candidate.length() <= 128) {
            return candidate;
        }
        String suffix = "-" + shortHash(cleaned);
        int maxBaseLength = Math.max(1, 128 - prefix.length() - suffix.length());
        return prefix + cleaned.substring(0, Math.min(cleaned.length(), maxBaseLength)) + suffix;
    }

    protected List<String> validateImport(QuestionBankImportRequest request) {
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

    protected boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    protected String nonBlank(String first, String fallback) {
        return !isBlank(first) ? first : fallback;
    }

    private boolean isPrivateScope(QuestionBankImportScope scope) {
        return scope != null && "PRIVATE".equalsIgnoreCase(scope.scope());
    }

    private boolean matchesScope(KnowledgeAtom atom, QuestionBankImportScope scope) {
        if (atom == null || scope == null) {
            return false;
        }
        return equalsIgnoreCase(atom.getScope(), scope.scope())
                && equalsLong(atom.getOwnerUserId(), scope.ownerUserId())
                && equalsLong(atom.getPositionId(), scope.positionId())
                && equalsLong(atom.getKnowledgeBaseId(), scope.knowledgeBaseId());
    }

    private String shortHash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 6 && i < hash.length; i++) {
                sb.append(String.format("%02x", hash[i]));
            }
            return sb.toString();
        } catch (Exception e) {
            return Integer.toHexString(value.hashCode());
        }
    }

    private boolean equalsIgnoreCase(String first, String second) {
        if (first == null || second == null) {
            return first == second;
        }
        return first.equalsIgnoreCase(second);
    }

    private boolean equalsLong(Long first, Long second) {
        if (first == null || second == null) {
            return first == second;
        }
        return first.equals(second);
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
}

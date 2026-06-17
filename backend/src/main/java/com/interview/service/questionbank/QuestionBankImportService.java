package com.interview.service.questionbank;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.interview.dto.questionbank.KnowledgeAtomPayload;
import com.interview.dto.questionbank.QuestionBankImportPreviewResponse;
import com.interview.dto.questionbank.QuestionBankImportRequest;
import com.interview.dto.questionbank.QuestionBankImportResult;
import com.interview.entity.KnowledgeAtom;
import com.interview.entity.KnowledgeAtomImportBatch;
import com.interview.mapper.KnowledgeAtomImportBatchMapper;
import com.interview.mapper.KnowledgeAtomMapper;
import com.interview.mapper.KnowledgeAtomVersionMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

class QuestionBankImportService extends QuestionBankSupport {

    private final QuestionBankVectorSyncService vectorSyncService;

    QuestionBankImportService(KnowledgeAtomMapper atomMapper,
                              KnowledgeAtomVersionMapper versionMapper,
                              KnowledgeAtomImportBatchMapper batchMapper,
                              QuestionBankVectorSyncService vectorSyncService) {
        super(atomMapper, versionMapper, batchMapper);
        this.vectorSyncService = vectorSyncService;
    }

    List<String> validateImportPackage(QuestionBankImportRequest request) {
        return validateImport(request);
    }

    QuestionBankImportPreviewResponse previewImport(QuestionBankImportRequest request) {
        return previewImport(request, null);
    }

    QuestionBankImportPreviewResponse previewImport(QuestionBankImportRequest request, QuestionBankImportScope scope) {
        QuestionBankImportPreviewResponse response = new QuestionBankImportPreviewResponse();
        String batchId = nonBlank(request.getBatchId(), "qb-" + UUID.randomUUID());
        response.setBatchId(batchId);
        response.setMode(normalizeMode(request.getMode(), scope));
        response.setTargetCategory(request.getTargetCategory());
        response.setSourceRef(request.getSourceRef());
        response.setReceived(request.getAtoms() != null ? request.getAtoms().size() : 0);
        response.setErrors(validateImport(request));
        response.setBatchIdExists(batchExists(batchId));

        Map<String, Integer> seen = new LinkedHashMap<>();
        if (request.getAtoms() != null) {
            for (KnowledgeAtomPayload atom : request.getAtoms()) {
                if (!isBlank(atom.getId())) {
                    seen.merge(scopedAtomId(atom.getId(), scope), 1, Integer::sum);
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

    QuestionBankImportResult importBatch(QuestionBankImportRequest request) {
        return importBatch(request, null);
    }

    QuestionBankImportResult importBatch(QuestionBankImportRequest request, QuestionBankImportScope scope) {
        String mode = normalizeMode(request.getMode(), scope);
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
                KnowledgeAtom atom = toAtom(payload, request.getTargetCategory(), request.getSourceRef(), mode, scope);
                upsertAtom(atom, "import:" + batchId, scope);
                imported++;
                if (QuestionBankService.STATUS_PUBLISHED.equals(atom.getStatus())) {
                    if (shouldSyncOnPublish(scope)) {
                        if (vectorSyncService.syncAtom(atom)) published++;
                    } else {
                        published++;
                    }
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

    private boolean shouldSyncOnPublish(QuestionBankImportScope scope) {
        return scope == null || scope.syncOnPublish();
    }
}

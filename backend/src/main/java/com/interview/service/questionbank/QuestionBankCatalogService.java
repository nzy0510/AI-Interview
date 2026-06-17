package com.interview.service.questionbank;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.interview.dto.questionbank.QuestionBankAtomListItem;
import com.interview.dto.questionbank.QuestionBankAtomQueryRequest;
import com.interview.dto.questionbank.QuestionBankBatchDetailResponse;
import com.interview.dto.questionbank.QuestionBankBatchListItem;
import com.interview.dto.questionbank.QuestionBankPageResponse;
import com.interview.entity.KnowledgeAtom;
import com.interview.entity.KnowledgeAtomImportBatch;
import com.interview.mapper.KnowledgeAtomImportBatchMapper;
import com.interview.mapper.KnowledgeAtomMapper;
import com.interview.mapper.KnowledgeAtomVersionMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

class QuestionBankCatalogService extends QuestionBankSupport {

    QuestionBankCatalogService(KnowledgeAtomMapper atomMapper,
                               KnowledgeAtomVersionMapper versionMapper,
                               KnowledgeAtomImportBatchMapper batchMapper) {
        super(atomMapper, versionMapper, batchMapper);
    }

    List<Map<String, Object>> listCategories() {
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
            if (QuestionBankService.STATUS_PUBLISHED.equalsIgnoreCase(status)) item.put("published", count);
            if (QuestionBankService.STATUS_DRAFT.equalsIgnoreCase(status)) item.put("draft", count);
            if (QuestionBankService.STATUS_ARCHIVED.equalsIgnoreCase(status)) item.put("archived", count);
        }
        return new ArrayList<>(grouped.values());
    }

    QuestionBankPageResponse<QuestionBankAtomListItem> listAtoms(QuestionBankAtomQueryRequest request) {
        return listAtoms(request, null);
    }

    QuestionBankPageResponse<QuestionBankAtomListItem> listAtoms(QuestionBankAtomQueryRequest request,
                                                                 QuestionBankImportScope scope) {
        int page = Math.max(1, request.getPage());
        int size = Math.min(Math.max(1, request.getSize()), 100);
        List<String> batchAtomIds = !isBlank(request.getBatchId())
                ? batchAtomIds(request.getBatchId(), false)
                : List.of();

        QueryWrapper<KnowledgeAtom> countWrapper = buildAtomQuery(request, batchAtomIds, scope);
        long total = safeLong(atomMapper.selectCount(countWrapper));
        if (total == 0) {
            return QuestionBankPageResponse.of(0, page, size, List.of());
        }

        int offset = (page - 1) * size;
        QueryWrapper<KnowledgeAtom> listWrapper = buildAtomQuery(request, batchAtomIds, scope)
                .orderByDesc("update_time")
                .last("LIMIT " + offset + ", " + size);
        List<QuestionBankAtomListItem> items = atomMapper.selectList(listWrapper).stream()
                .map(this::toListItem)
                .collect(Collectors.toList());
        return QuestionBankPageResponse.of(total, page, size, items);
    }

    QuestionBankPageResponse<QuestionBankBatchListItem> listBatches(int pageValue, int sizeValue) {
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

    QuestionBankBatchDetailResponse getBatchDetail(String batchId) {
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
}

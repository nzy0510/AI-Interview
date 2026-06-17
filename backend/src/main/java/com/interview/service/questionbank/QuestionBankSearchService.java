package com.interview.service.questionbank;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.interview.config.PositionCategoryConfig;
import com.interview.dto.questionbank.QuestionBankSearchRequest;
import com.interview.dto.questionbank.QuestionBankSearchResponse;
import com.interview.dto.questionbank.QuestionBankSearchResult;
import com.interview.entity.KnowledgeAtom;
import com.interview.mapper.KnowledgeAtomImportBatchMapper;
import com.interview.mapper.KnowledgeAtomMapper;
import com.interview.mapper.KnowledgeAtomVersionMapper;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

class QuestionBankSearchService extends QuestionBankSupport {

    private static final int DEFAULT_SEARCH_LIMIT = 3;
    private static final int MAX_SEARCH_LIMIT = 30;

    private final PositionCategoryConfig categoryConfig;
    private final QdrantVectorService qdrantVectorService;
    private final QuestionBankFallbackTerms fallbackTerms = new QuestionBankFallbackTerms();

    QuestionBankSearchService(KnowledgeAtomMapper atomMapper,
                              KnowledgeAtomVersionMapper versionMapper,
                              KnowledgeAtomImportBatchMapper batchMapper,
                              PositionCategoryConfig categoryConfig,
                              QdrantVectorService qdrantVectorService) {
        super(atomMapper, versionMapper, batchMapper);
        this.categoryConfig = categoryConfig;
        this.qdrantVectorService = qdrantVectorService;
    }

    List<QuestionBankSearchResult> search(QuestionBankSearchRequest request) {
        return searchWithMetadata(request).getResults();
    }

    QuestionBankSearchResponse searchWithMetadata(QuestionBankSearchRequest request) {
        int limit = request.getLimit() > 0 ? Math.min(request.getLimit(), MAX_SEARCH_LIMIT) : DEFAULT_SEARCH_LIMIT;
        String query = request.getQuery() != null ? request.getQuery().trim() : "";
        if (query.length() <= 2) {
            return QuestionBankSearchResponse.builder()
                    .results(List.of())
                    .strategy("SKIPPED")
                    .build();
        }

        List<String> categories = normalizeCategories(request);
        List<String> exclude = request.getExcludeAtomIds() != null ? request.getExcludeAtomIds() : List.of();

        List<QdrantVectorService.VectorHit> hits;
        boolean degraded = false;
        try {
            hits = qdrantVectorService.search(query, categories, exclude, limit,
                    request.getScope(), request.getOwnerUserId(), request.getPositionId(), request.getKnowledgeBaseId());
        } catch (RuntimeException e) {
            log.warn("Qdrant unavailable, using MySQL fallback: {}", e.getMessage());
            hits = List.of();
            degraded = true;
        }
        List<QuestionBankSearchResult> results = loadHits(hits, request);
        if (!results.isEmpty()) {
            return QuestionBankSearchResponse.builder()
                    .results(results.stream().limit(limit).collect(Collectors.toList()))
                    .strategy("QDRANT_VECTOR")
                    .build();
        }
        return QuestionBankSearchResponse.builder()
                .results(fallbackSearch(query, categories, exclude, limit, request))
                .strategy(degraded ? "MYSQL_FALLBACK_DEGRADED" : "MYSQL_FALLBACK")
                .build();
    }

    String buildPromptContext(KnowledgeAtom atom) {
        return "考核点: " + atom.getSubject() + "\n"
                + "核心原理与标准答案: " + atom.getPrinciples() + "\n"
                + (isBlank(atom.getPitfalls()) ? "" : "面试常见陷阱与候选人易错点: " + atom.getPitfalls() + "\n")
                + (isBlank(atom.getFollowUpPathsJson()) ? "" : "推荐的深度追问路径: " + atom.getFollowUpPathsJson() + "\n");
    }

    private List<String> normalizeCategories(QuestionBankSearchRequest request) {
        if (request.getCategories() != null && !request.getCategories().isEmpty()) {
            return request.getCategories();
        }
        if (hasStructuredScope(request)) {
            return List.of();
        }
        if (!isBlank(request.getPosition())) {
            return categoryConfig.getCategoriesFor(request.getPosition());
        }
        return List.of();
    }

    private boolean hasStructuredScope(QuestionBankSearchRequest request) {
        return !isBlank(request.getScope())
                || request.getOwnerUserId() != null
                || request.getPositionId() != null
                || request.getKnowledgeBaseId() != null;
    }

    private List<QuestionBankSearchResult> loadHits(List<QdrantVectorService.VectorHit> hits, QuestionBankSearchRequest request) {
        if (hits == null || hits.isEmpty()) return List.of();
        List<String> atomIds = hits.stream().map(QdrantVectorService.VectorHit::getAtomId).collect(Collectors.toList());
        QueryWrapper<KnowledgeAtom> wrapper = new QueryWrapper<KnowledgeAtom>()
                .in("atom_id", atomIds)
                .eq("status", QuestionBankService.STATUS_PUBLISHED);
        applySearchScope(wrapper, request);
        List<KnowledgeAtom> atoms = atomMapper.selectList(wrapper);
        Map<String, KnowledgeAtom> byId = atoms.stream().collect(Collectors.toMap(KnowledgeAtom::getAtomId, a -> a));
        return hits.stream()
                .filter(hit -> byId.containsKey(hit.getAtomId()))
                .map(hit -> toResult(byId.get(hit.getAtomId()), hit.getScore()))
                .collect(Collectors.toList());
    }

    private List<QuestionBankSearchResult> fallbackSearch(String query,
                                                          List<String> categories,
                                                          List<String> exclude,
                                                          int limit,
                                                          QuestionBankSearchRequest request) {
        List<String> terms = fallbackTerms.from(query);
        QueryWrapper<KnowledgeAtom> wrapper = new QueryWrapper<>();
        wrapper.eq("status", QuestionBankService.STATUS_PUBLISHED)
                .in(categories != null && !categories.isEmpty(), "category", categories)
                .notIn(exclude != null && !exclude.isEmpty(), "atom_id", exclude);
        applySearchScope(wrapper, request);
        wrapper.and(w -> {
                    for (int i = 0; i < terms.size(); i++) {
                        if (i > 0) w.or();
                        String term = terms.get(i);
                        w.like("subject", term)
                                .or().like("principles", term)
                                .or().like("tags_json", term);
                    }
                })
                .orderByDesc("update_time")
                .last("LIMIT " + Math.max(1, limit));
        return atomMapper.selectList(wrapper).stream()
                .sorted(Comparator.comparing(KnowledgeAtom::getUpdateTime, Comparator.nullsLast(Comparator.reverseOrder())))
                .map(atom -> toResult(atom, 0.0))
                .collect(Collectors.toList());
    }

    private void applySearchScope(QueryWrapper<KnowledgeAtom> wrapper, QuestionBankSearchRequest request) {
        if (request == null) {
            return;
        }
        if (!isBlank(request.getScope())) {
            wrapper.eq("scope", request.getScope().trim().toUpperCase());
        }
        if (request.getOwnerUserId() != null) {
            wrapper.eq("owner_user_id", request.getOwnerUserId());
        }
        if (request.getPositionId() != null) {
            wrapper.eq("position_id", request.getPositionId());
        }
        if (request.getKnowledgeBaseId() != null) {
            wrapper.eq("knowledge_base_id", request.getKnowledgeBaseId());
        }
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
}

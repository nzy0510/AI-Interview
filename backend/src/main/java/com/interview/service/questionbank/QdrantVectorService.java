package com.interview.service.questionbank;

import com.interview.entity.KnowledgeAtom;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.EmbeddingModel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
public class QdrantVectorService {

    private final RestTemplate restTemplate = new RestTemplate();
    private final EmbeddingModel embeddingModel;

    @Value("${question-bank.qdrant.enabled:true}")
    private boolean enabled;

    @Value("${question-bank.qdrant.url:http://localhost:6333}")
    private String qdrantUrl;

    @Value("${question-bank.qdrant.collection:interview_atoms}")
    private String collectionName;

    @Value("${question-bank.qdrant.vector-size:384}")
    private int vectorSize;

    public QdrantVectorService(EmbeddingModel embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

    public boolean ensureCollection() {
        if (!enabled) return false;
        try {
            restTemplate.getForObject(endpoint("/collections/" + collectionName), Map.class);
            return true;
        } catch (RestClientException ignored) {
            try {
                Map<String, Object> body = Map.of(
                        "vectors", Map.of(
                                "size", vectorSize,
                                "distance", "Cosine"
                        )
                );
                restTemplate.put(endpoint("/collections/" + collectionName), jsonEntity(body));
                log.info("Qdrant collection initialized: {}", collectionName);
                return true;
            } catch (RestClientException e) {
                log.warn("Qdrant collection initialization skipped: {}", e.getMessage());
                return false;
            }
        }
    }

    public boolean upsert(KnowledgeAtom atom) {
        if (!enabled || atom == null || !"PUBLISHED".equalsIgnoreCase(atom.getStatus())) {
            return false;
        }
        if (!ensureCollection()) return false;
        try {
            List<Float> vector = embed(buildSearchText(atom));
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("atom_id", atom.getAtomId());
            payload.put("subject", atom.getSubject());
            payload.put("category", atom.getCategory());
            payload.put("difficulty", atom.getDifficulty());
            payload.put("status", atom.getStatus());
            payload.put("updated_at", LocalDateTime.now().toString());

            Map<String, Object> point = new LinkedHashMap<>();
            point.put("id", pointId(atom.getAtomId()).toString());
            point.put("vector", vector);
            point.put("payload", payload);

            restTemplate.put(endpoint("/collections/" + collectionName + "/points?wait=true"),
                    jsonEntity(Map.of("points", List.of(point))));
            return true;
        } catch (Exception e) {
            log.warn("Qdrant upsert failed for atom {}: {}", atom.getAtomId(), e.getMessage());
            return false;
        }
    }

    public boolean delete(String atomId) {
        if (!enabled || atomId == null || atomId.isBlank()) return false;
        try {
            Map<String, Object> body = Map.of("points", List.of(pointId(atomId).toString()));
            restTemplate.postForObject(endpoint("/collections/" + collectionName + "/points/delete?wait=true"),
                    jsonEntity(body), Map.class);
            return true;
        } catch (Exception e) {
            log.warn("Qdrant delete failed for atom {}: {}", atomId, e.getMessage());
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    public List<VectorHit> search(String query, List<String> categories, List<String> excludeAtomIds, int limit) {
        if (!enabled || query == null || query.isBlank()) return List.of();
        if (!ensureCollection()) return List.of();
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("vector", embed(query));
            body.put("limit", Math.max(1, limit));
            body.put("with_payload", true);
            Map<String, Object> filter = buildFilter(categories, excludeAtomIds);
            if (!filter.isEmpty()) body.put("filter", filter);

            Map<String, Object> response = restTemplate.postForObject(
                    endpoint("/collections/" + collectionName + "/points/search"),
                    jsonEntity(body),
                    Map.class
            );
            Object raw = response != null ? response.get("result") : null;
            if (!(raw instanceof List<?> rows)) return List.of();
            List<VectorHit> hits = new ArrayList<>();
            for (Object row : rows) {
                if (!(row instanceof Map<?, ?> item)) continue;
                Object payloadRaw = item.get("payload");
                if (!(payloadRaw instanceof Map<?, ?> payload)) continue;
                Object atomId = payload.get("atom_id");
                Object score = item.get("score");
                if (atomId != null) {
                    hits.add(new VectorHit(String.valueOf(atomId),
                            score instanceof Number ? ((Number) score).doubleValue() : 0.0));
                }
            }
            return hits;
        } catch (Exception e) {
            log.warn("Qdrant search skipped: {}", e.getMessage());
            return List.of();
        }
    }

    private Map<String, Object> buildFilter(List<String> categories, List<String> excludeAtomIds) {
        List<Object> must = new ArrayList<>();
        must.add(Map.of("key", "status", "match", Map.of("value", "PUBLISHED")));
        if (categories != null && !categories.isEmpty()) {
            must.add(Map.of("key", "category", "match", Map.of("any", categories)));
        }
        List<Object> mustNot = new ArrayList<>();
        if (excludeAtomIds != null && !excludeAtomIds.isEmpty()) {
            mustNot.add(Map.of("key", "atom_id", "match", Map.of("any", excludeAtomIds)));
        }
        Map<String, Object> filter = new LinkedHashMap<>();
        filter.put("must", must);
        if (!mustNot.isEmpty()) filter.put("must_not", mustNot);
        return filter;
    }

    private List<Float> embed(String text) {
        Embedding embedding = embeddingModel.embed(text).content();
        return embedding.vectorAsList();
    }

    private String buildSearchText(KnowledgeAtom atom) {
        return "考核点: " + atom.getSubject() + "\n"
                + "核心原理与标准答案: " + atom.getPrinciples() + "\n"
                + "面试常见陷阱与候选人易错点: " + nullToEmpty(atom.getPitfalls()) + "\n"
                + "推荐的深度追问路径: " + nullToEmpty(atom.getFollowUpPathsJson());
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private UUID pointId(String atomId) {
        return UUID.nameUUIDFromBytes(atomId.getBytes(StandardCharsets.UTF_8));
    }

    private String endpoint(String path) {
        return qdrantUrl.replaceAll("/+$", "") + path;
    }

    private HttpEntity<Map<String, Object>> jsonEntity(Map<String, Object> body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(body, headers);
    }

    @Data
    @AllArgsConstructor
    public static class VectorHit {
        private String atomId;
        private double score;
    }
}

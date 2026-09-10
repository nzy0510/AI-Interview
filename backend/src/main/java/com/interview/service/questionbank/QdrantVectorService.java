package com.interview.service.questionbank;

import com.interview.config.ExternalHttpClientFactory;
import com.interview.entity.KnowledgeAtom;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.EmbeddingModel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientResponseException;
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

    private final RestTemplate restTemplate;
    private final EmbeddingModel embeddingModel;

    @Value("${question-bank.qdrant.enabled:true}")
    private boolean enabled;

    @Value("${question-bank.qdrant.url:http://localhost:6333}")
    private String qdrantUrl;

    @Value("${question-bank.qdrant.api-key:}")
    private String apiKey = "";

    @Value("${question-bank.qdrant.initialize-payload-indexes:false}")
    private boolean initializePayloadIndexes;

    private volatile boolean payloadIndexesReady;

    @Value("${question-bank.qdrant.collection:interview_atoms}")
    private String collectionName;

    @Value("${question-bank.qdrant.vector-size:384}")
    private int vectorSize;

    @Value("${app.embedding.query-prefix:}")
    private String queryPrefix;

    @Value("${app.embedding.passage-prefix:}")
    private String passagePrefix;

    @Autowired
    public QdrantVectorService(
            EmbeddingModel embeddingModel,
            @Value("${question-bank.qdrant.connect-timeout-ms:3000}") int connectTimeoutMs,
            @Value("${question-bank.qdrant.read-timeout-ms:5000}") int readTimeoutMs) {
        this(embeddingModel, ExternalHttpClientFactory.create(connectTimeoutMs, readTimeoutMs));
    }

    QdrantVectorService(EmbeddingModel embeddingModel) {
        this(embeddingModel, ExternalHttpClientFactory.create(3000, 5000));
    }

    QdrantVectorService(EmbeddingModel embeddingModel, RestTemplate restTemplate) {
        this.embeddingModel = embeddingModel;
        this.restTemplate = restTemplate;
    }

    public boolean ensureCollection() {
        return ensureCollection(true);
    }

    public boolean isAvailable() {
        try {
            return ensureCollection(false);
        } catch (RuntimeException e) {
            return false;
        }
    }

    private boolean ensureCollection(boolean allowInitialization) {
        if (!enabled) return false;
        try {
            Map<?, ?> collection = restTemplate.exchange(endpoint("/collections/" + collectionName),
                    HttpMethod.GET, new HttpEntity<>(requestHeaders()), Map.class).getBody();
            Integer actualVectorSize = extractVectorSize(collection);
            if (actualVectorSize != null && actualVectorSize != vectorSize) {
                throw new IllegalStateException("Qdrant collection " + collectionName
                        + " vector size mismatch: expected " + vectorSize
                        + ", actual " + actualVectorSize);
            }
        } catch (HttpClientErrorException.NotFound e) {
            if (!allowInitialization) return false;
            try {
                Map<String, Object> body = Map.of(
                        "vectors", Map.of(
                                "size", vectorSize,
                                "distance", "Cosine"
                        )
                );
                restTemplate.put(endpoint("/collections/" + collectionName), jsonEntity(body));
                payloadIndexesReady = false;
                log.info("Qdrant collection initialized: {}", collectionName);
            } catch (RestClientException creationError) {
                log.warn("Qdrant collection initialization failed: {}", failureSummary(creationError));
                return false;
            }
        } catch (RestClientException e) {
            log.warn("Qdrant collection check failed: {}", failureSummary(e));
            return false;
        }
        try {
            if (allowInitialization && initializePayloadIndexes) ensurePayloadIndexes();
            return true;
        } catch (RestClientException e) {
            log.warn("Qdrant payload index initialization failed: {}", failureSummary(e));
            return false;
        }
    }

    private synchronized void ensurePayloadIndexes() {
        if (payloadIndexesReady) return;
        for (String field : List.of("atom_id", "category", "status", "publication_status", "vector_status", "scope",
                "owner_user_id", "position_id", "knowledge_base_id")) {
            String schema = field.endsWith("_id") && !"atom_id".equals(field) ? "integer" : "keyword";
            restTemplate.put(endpoint("/collections/" + collectionName + "/index?wait=true"),
                    jsonEntity(Map.of("field_name", field, "field_schema", schema)));
        }
        payloadIndexesReady = true;
    }

    private Integer extractVectorSize(Map<?, ?> collection) {
        Object current = collection;
        for (String key : List.of("result", "config", "params", "vectors")) {
            if (!(current instanceof Map<?, ?> map)) return null;
            current = map.get(key);
        }
        if (!(current instanceof Map<?, ?> vectors)) return null;
        Object size = vectors.get("size");
        return size instanceof Number number ? number.intValue() : null;
    }

    public boolean upsert(KnowledgeAtom atom) {
        if (!enabled || atom == null || !"PUBLISHED".equalsIgnoreCase(atom.getStatus())) {
            return false;
        }
        try {
            if (!ensureCollection()) return false;
            List<Float> vector = embed(withPrefix(passagePrefix, buildSearchText(atom)));
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("atom_id", atom.getAtomId());
            payload.put("subject", atom.getSubject());
            payload.put("category", atom.getCategory());
            payload.put("difficulty", atom.getDifficulty());
            payload.put("status", atom.getStatus());
            payload.put("publication_status", atom.getPublicationStatus());
            payload.put("vector_status", "SYNCED");
            payload.put("scope", atom.getScope());
            payload.put("owner_user_id", atom.getOwnerUserId());
            payload.put("position_id", atom.getPositionId());
            payload.put("knowledge_base_id", atom.getKnowledgeBaseId());
            payload.put("source_file_id", atom.getSourceFileId());
            payload.put("updated_at", LocalDateTime.now().toString());

            Map<String, Object> point = new LinkedHashMap<>();
            point.put("id", pointId(atom.getAtomId()).toString());
            point.put("vector", vector);
            point.put("payload", payload);

            restTemplate.put(endpoint("/collections/" + collectionName + "/points?wait=true"),
                    jsonEntity(Map.of("points", List.of(point))));
            return true;
        } catch (Exception e) {
            log.warn("Qdrant upsert failed for atom {}: {}", atom.getAtomId(), failureSummary(e));
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
            log.warn("Qdrant delete failed for atom {}: {}", atomId, failureSummary(e));
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    public List<VectorHit> search(String query, List<String> categories, List<String> excludeAtomIds, int limit) {
        return search(query, categories, excludeAtomIds, limit, null, null, null, null);
    }

    @SuppressWarnings("unchecked")
    public List<VectorHit> search(String query,
                                  List<String> categories,
                                  List<String> excludeAtomIds,
                                  int limit,
                                  String scope,
                                  Long ownerUserId,
                                  Long positionId,
                                  Long knowledgeBaseId) {
        if (!enabled || query == null || query.isBlank()) return List.of();
        if (!ensureCollection(false)) {
            throw new IllegalStateException("Qdrant collection is unavailable");
        }
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("vector", embed(withPrefix(queryPrefix, query)));
            body.put("limit", Math.max(1, limit));
            body.put("with_payload", true);
            Map<String, Object> filter = buildFilter(categories, excludeAtomIds, scope, ownerUserId, positionId, knowledgeBaseId);
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
            log.warn("Qdrant search failed: {}", failureSummary(e));
            throw new IllegalStateException("Qdrant vector search failed");
        }
    }

    private Map<String, Object> buildFilter(List<String> categories,
                                            List<String> excludeAtomIds,
                                            String scope,
                                            Long ownerUserId,
                                            Long positionId,
                                            Long knowledgeBaseId) {
        List<Object> must = new ArrayList<>();
        must.add(Map.of("key", "status", "match", Map.of("value", "PUBLISHED")));
        must.add(Map.of("key", "publication_status", "match", Map.of("value", "PUBLISHED")));
        must.add(Map.of("key", "vector_status", "match", Map.of("value", "SYNCED")));
        String normalizedScope = scope == null || scope.isBlank() ? null : scope.trim().toUpperCase();
        if (normalizedScope != null) {
            must.add(Map.of("key", "scope", "match", Map.of("value", normalizedScope)));
        }
        if (ownerUserId != null) {
            must.add(Map.of("key", "owner_user_id", "match", Map.of("value", ownerUserId)));
        } else if ("PUBLIC".equals(normalizedScope)) {
            must.add(Map.of("is_empty", Map.of("key", "owner_user_id")));
        }
        if (positionId != null) {
            must.add(Map.of("key", "position_id", "match", Map.of("value", positionId)));
        }
        if (knowledgeBaseId != null) {
            must.add(Map.of("key", "knowledge_base_id", "match", Map.of("value", knowledgeBaseId)));
        }
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

    private String withPrefix(String prefix, String text) {
        if (prefix == null || prefix.isBlank()) return text;
        if (Character.isWhitespace(prefix.charAt(prefix.length() - 1))) return prefix + text;
        return prefix + " " + text;
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
        return new HttpEntity<>(body, requestHeaders());
    }

    private HttpHeaders requestHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (apiKey != null && !apiKey.isBlank()) headers.set("api-key", apiKey.trim());
        return headers;
    }

    private String failureSummary(Exception error) {
        return error instanceof RestClientResponseException response
                ? "HTTP " + response.getStatusCode().value() : error.getClass().getSimpleName();
    }

    @Data
    @AllArgsConstructor
    public static class VectorHit {
        private String atomId;
        private double score;
    }
}

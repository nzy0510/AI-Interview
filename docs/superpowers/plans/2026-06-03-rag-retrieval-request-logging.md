# RAG Retrieval Request Logging Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Record every interview RAG retrieval attempt, including zero-hit and failed requests, while linking existing hit-level logs to a stable request identifier.

**Architecture:** Add a request-level MySQL table and MyBatis entity/mapper. Introduce a metadata-bearing question-bank search result so the interview runtime can record retrieval strategy, candidate count, latency, and failure state without changing the existing admin search-preview API contract. Keep request logging in `InterviewServiceImpl`, where user, record, turn, phase, and production query context are available.

**Tech Stack:** Java 17, Spring Boot 3.2.4, MyBatis-Plus, MySQL 8, Flyway, JUnit 5, Mockito, AssertJ

---

## File Structure

**Create**

- `backend/src/main/resources/db/migration/V11__add_rag_retrieval_request_log.sql`
  Creates the request-level log table and links hit-level logs with `request_id`.
- `backend/src/main/java/com/interview/entity/RagRetrievalRequestLog.java`
  MyBatis entity for one retrieval attempt.
- `backend/src/main/java/com/interview/mapper/RagRetrievalRequestLogMapper.java`
  MyBatis mapper for request log inserts.
- `backend/src/main/java/com/interview/dto/questionbank/QuestionBankSearchResponse.java`
  Internal search metadata wrapper containing results and strategy.
- `backend/src/test/java/com/interview/service/questionbank/QuestionBankServiceSearchTest.java`
  Focused tests for vector and fallback strategy metadata.

**Modify**

- `backend/src/main/java/com/interview/entity/RagRetrievalLog.java`
  Adds `requestId`.
- `backend/src/main/java/com/interview/service/questionbank/QuestionBankService.java`
  Adds `searchWithMetadata`; keeps `search` as a compatibility delegate.
- `backend/src/main/java/com/interview/service/impl/InterviewServiceImpl.java`
  Measures retrieval latency, writes request logs for success, zero hit, and failure, and links hit rows.
- `backend/src/test/java/com/interview/service/InterviewServiceImplTest.java`
  Verifies request log behavior and existing failure tolerance.
- `README.md`
  Documents request-level and hit-level RAG logging.
- `CHANGELOG.md`
  Records the new retrieval observability capability.

## Task 1: Add The Request-Level Database Schema

**Files:**
- Create: `backend/src/main/resources/db/migration/V11__add_rag_retrieval_request_log.sql`

- [ ] **Step 1: Write the Flyway migration**

```sql
-- V11: Record every interview RAG retrieval request, including zero-hit attempts.

CREATE TABLE IF NOT EXISTS rag_retrieval_request_log (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  request_id VARCHAR(36) NOT NULL COMMENT 'Stable request identifier shared with hit rows',
  user_id BIGINT NOT NULL COMMENT 'User that owns the interview',
  record_id BIGINT NOT NULL COMMENT 'Interview record ID',
  turn_index INT NOT NULL COMMENT 'Conversation turn, starting at 1',
  position VARCHAR(64) DEFAULT NULL COMMENT 'Interview position',
  phase VARCHAR(32) DEFAULT NULL COMMENT 'Interview phase',
  query_text VARCHAR(500) DEFAULT NULL COMMENT 'Production retrieval query, access restricted',
  requested_limit INT NOT NULL DEFAULT 3 COMMENT 'Requested result limit',
  candidate_count INT NOT NULL DEFAULT 0 COMMENT 'Returned candidate count, including zero',
  retrieval_strategy VARCHAR(32) NOT NULL COMMENT 'QDRANT_VECTOR, MYSQL_FALLBACK, SKIPPED, or FAILED',
  latency_ms BIGINT NOT NULL DEFAULT 0 COMMENT 'Question-bank search latency in milliseconds',
  status VARCHAR(16) NOT NULL COMMENT 'SUCCESS or FAILED',
  error_message VARCHAR(500) DEFAULT NULL COMMENT 'Sanitized failure message',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_rag_retrieval_request_id (request_id),
  INDEX idx_rag_request_record_turn (record_id, turn_index),
  INDEX idx_rag_request_position_time (position, create_time),
  INDEX idx_rag_request_status_time (status, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='One row per interview RAG retrieval request';

ALTER TABLE rag_retrieval_log
  ADD COLUMN request_id VARCHAR(36) DEFAULT NULL COMMENT 'Links hit row to rag_retrieval_request_log',
  ADD INDEX idx_rag_retrieval_request_id (request_id);
```

- [ ] **Step 2: Verify migration naming and SQL formatting**

Run:

```powershell
Get-ChildItem backend/src/main/resources/db/migration
git diff --check
```

Expected: `V11__add_rag_retrieval_request_log.sql` appears after `V10__remove_mcp_feature.sql`; `git diff --check` prints no errors.

- [ ] **Step 3: Commit the schema**

```bash
git add backend/src/main/resources/db/migration/V11__add_rag_retrieval_request_log.sql
git commit -m "feat: add rag retrieval request log schema"
```

## Task 2: Add Request Log And Search Metadata Types

**Files:**
- Create: `backend/src/main/java/com/interview/entity/RagRetrievalRequestLog.java`
- Create: `backend/src/main/java/com/interview/mapper/RagRetrievalRequestLogMapper.java`
- Create: `backend/src/main/java/com/interview/dto/questionbank/QuestionBankSearchResponse.java`
- Modify: `backend/src/main/java/com/interview/entity/RagRetrievalLog.java`

- [ ] **Step 1: Add the request log entity**

```java
package com.interview.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("rag_retrieval_request_log")
public class RagRetrievalRequestLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String requestId;
    private Long userId;
    private Long recordId;
    private Integer turnIndex;
    private String position;
    private String phase;
    private String queryText;
    private Integer requestedLimit;
    private Integer candidateCount;
    private String retrievalStrategy;
    private Long latencyMs;
    private String status;
    private String errorMessage;
    private LocalDateTime createTime;
}
```

- [ ] **Step 2: Add the mapper**

```java
package com.interview.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.interview.entity.RagRetrievalRequestLog;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface RagRetrievalRequestLogMapper extends BaseMapper<RagRetrievalRequestLog> {
}
```

- [ ] **Step 3: Add the internal search metadata wrapper**

```java
package com.interview.dto.questionbank;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class QuestionBankSearchResponse {
    private List<QuestionBankSearchResult> results;
    private String strategy;
}
```

- [ ] **Step 4: Link hit-level logs to request IDs**

Add this field to `RagRetrievalLog` after `recordId`:

```java
private String requestId;
```

- [ ] **Step 5: Compile the backend**

Run:

```powershell
cd backend
mvn -DskipTests compile
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 6: Commit the persistence types**

```bash
git add backend/src/main/java/com/interview/entity/RagRetrievalRequestLog.java backend/src/main/java/com/interview/mapper/RagRetrievalRequestLogMapper.java backend/src/main/java/com/interview/dto/questionbank/QuestionBankSearchResponse.java backend/src/main/java/com/interview/entity/RagRetrievalLog.java
git commit -m "feat: add rag retrieval request log types"
```

## Task 3: Return Retrieval Strategy Metadata From QuestionBankService

**Files:**
- Create: `backend/src/test/java/com/interview/service/questionbank/QuestionBankServiceSearchTest.java`
- Modify: `backend/src/main/java/com/interview/service/questionbank/QuestionBankService.java`

- [ ] **Step 1: Write failing vector strategy and fallback strategy tests**

```java
package com.interview.service.questionbank;

import com.interview.config.PositionCategoryConfig;
import com.interview.dto.questionbank.QuestionBankSearchRequest;
import com.interview.dto.questionbank.QuestionBankSearchResponse;
import com.interview.entity.KnowledgeAtom;
import com.interview.mapper.KnowledgeAtomImportBatchMapper;
import com.interview.mapper.KnowledgeAtomMapper;
import com.interview.mapper.KnowledgeAtomVersionMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QuestionBankServiceSearchTest {

    @Mock private KnowledgeAtomMapper atomMapper;
    @Mock private KnowledgeAtomVersionMapper versionMapper;
    @Mock private KnowledgeAtomImportBatchMapper batchMapper;
    @Mock private PositionCategoryConfig categoryConfig;
    @Mock private QdrantVectorService qdrantVectorService;

    @InjectMocks private QuestionBankService service;

    @Test
    void shouldReportQdrantVectorStrategyWhenVectorHitsLoadPublishedAtoms() {
        QuestionBankSearchRequest request = request();
        KnowledgeAtom atom = atom("atom-1");
        when(categoryConfig.getCategoriesFor("AI大模型")).thenReturn(List.of("AI大模型"));
        when(qdrantVectorService.search(any(), any(), any(), any(Integer.class)))
                .thenReturn(List.of(new QdrantVectorService.VectorHit("atom-1", 0.91)));
        when(atomMapper.selectList(any())).thenReturn(List.of(atom));

        QuestionBankSearchResponse response = service.searchWithMetadata(request);

        assertThat(response.getStrategy()).isEqualTo("QDRANT_VECTOR");
        assertThat(response.getResults()).extracting("atomId").containsExactly("atom-1");
    }

    @Test
    void shouldReportMysqlFallbackStrategyWhenVectorSearchHasNoUsableHits() {
        QuestionBankSearchRequest request = request();
        when(categoryConfig.getCategoriesFor("AI大模型")).thenReturn(List.of("AI大模型"));
        when(qdrantVectorService.search(any(), any(), any(), any(Integer.class))).thenReturn(List.of());
        when(atomMapper.selectList(any())).thenReturn(List.of());

        QuestionBankSearchResponse response = service.searchWithMetadata(request);

        assertThat(response.getStrategy()).isEqualTo("MYSQL_FALLBACK");
        assertThat(response.getResults()).isEmpty();
    }

    private QuestionBankSearchRequest request() {
        QuestionBankSearchRequest request = new QuestionBankSearchRequest();
        request.setPosition("AI大模型");
        request.setQuery("Transformer 自注意力");
        request.setLimit(3);
        return request;
    }

    private KnowledgeAtom atom(String atomId) {
        KnowledgeAtom atom = new KnowledgeAtom();
        atom.setAtomId(atomId);
        atom.setSubject("自注意力");
        atom.setCategory("AI大模型");
        atom.setPrinciples("原理");
        atom.setStatus(QuestionBankService.STATUS_PUBLISHED);
        return atom;
    }
}
```

- [ ] **Step 2: Run the focused test and verify it fails**

Run:

```powershell
cd backend
mvn -Dtest=QuestionBankServiceSearchTest test
```

Expected: FAIL because `searchWithMetadata` does not exist.

- [ ] **Step 3: Implement `searchWithMetadata` and preserve `search` compatibility**

Replace the current `search` method body with:

```java
public List<QuestionBankSearchResult> search(QuestionBankSearchRequest request) {
    return searchWithMetadata(request).getResults();
}

public QuestionBankSearchResponse searchWithMetadata(QuestionBankSearchRequest request) {
    int limit = request.getLimit() > 0 ? Math.min(request.getLimit(), 10) : 3;
    String query = request.getQuery() != null ? request.getQuery().trim() : "";
    if (query.length() <= 2) {
        return QuestionBankSearchResponse.builder()
                .results(List.of())
                .strategy("SKIPPED")
                .build();
    }

    List<String> categories = normalizeCategories(request);
    List<String> exclude = request.getExcludeAtomIds() != null ? request.getExcludeAtomIds() : List.of();

    List<QdrantVectorService.VectorHit> hits =
            qdrantVectorService.search(query, categories, exclude, limit);
    List<QuestionBankSearchResult> results = loadHits(hits);
    if (!results.isEmpty()) {
        return QuestionBankSearchResponse.builder()
                .results(results.stream().limit(limit).collect(Collectors.toList()))
                .strategy("QDRANT_VECTOR")
                .build();
    }
    return QuestionBankSearchResponse.builder()
            .results(fallbackSearch(query, categories, exclude, limit))
            .strategy("MYSQL_FALLBACK")
            .build();
}
```

Add the import:

```java
import com.interview.dto.questionbank.QuestionBankSearchResponse;
```

- [ ] **Step 4: Run the focused test**

Run:

```powershell
cd backend
mvn -Dtest=QuestionBankServiceSearchTest test
```

Expected: PASS.

- [ ] **Step 5: Run existing question-bank controller tests**

Run:

```powershell
cd backend
mvn -Dtest=QuestionBankAdminControllerTest,QuestionBankImportContractTest test
```

Expected: PASS; the existing `search` API still returns a list.

- [ ] **Step 6: Commit the search metadata boundary**

```bash
git add backend/src/test/java/com/interview/service/questionbank/QuestionBankServiceSearchTest.java backend/src/main/java/com/interview/service/questionbank/QuestionBankService.java
git commit -m "feat: expose question bank retrieval strategy"
```

## Task 4: Record Successful, Zero-Hit, And Failed Retrieval Requests

**Files:**
- Modify: `backend/src/test/java/com/interview/service/InterviewServiceImplTest.java`
- Modify: `backend/src/main/java/com/interview/service/impl/InterviewServiceImpl.java`

- [ ] **Step 1: Add the request log mapper mock**

Add imports:

```java
import com.interview.dto.questionbank.QuestionBankSearchResponse;
import com.interview.entity.RagRetrievalRequestLog;
import com.interview.mapper.RagRetrievalRequestLogMapper;
```

Add the mock:

```java
@Mock
private RagRetrievalRequestLogMapper ragRetrievalRequestLogMapper;
```

- [ ] **Step 2: Write a failing zero-hit request logging test**

```java
@Test
@DisplayName("题库零命中时仍记录成功的请求级检索日志")
void shouldLogSuccessfulZeroHitRetrievalRequest() {
    InterviewRecord record = technicalRecord(22L);
    when(interviewRecordMapper.selectOne(any())).thenReturn(record);
    when(sessionStore.load(22L)).thenReturn(new ArrayList<>());
    when(sessionStore.loadUsedAtoms(22L)).thenReturn(List.of());
    when(sessionStore.loadTailoredQuestions(22L)).thenReturn(List.of());
    when(interviewTurnPlanner.determineNextPhase(any(), anyList())).thenReturn(InterviewPhase.TECHNICAL);
    when(questionBankService.searchWithMetadata(any())).thenReturn(QuestionBankSearchResponse.builder()
            .results(List.of())
            .strategy("MYSQL_FALLBACK")
            .build());
    when(interviewTurnPlanner.plan(any(), anyList(), any(), any()))
            .thenReturn(new InterviewTurnPlanner.InterviewTurnPlan(InterviewPhase.TECHNICAL, "technical"));
    stubStreamingResponse("继续");

    interviewService.chatStream(1L, 22L, "我不太清楚");

    ArgumentCaptor<RagRetrievalRequestLog> captor = ArgumentCaptor.forClass(RagRetrievalRequestLog.class);
    verify(ragRetrievalRequestLogMapper).insert(captor.capture());
    assertThat(captor.getValue().getStatus()).isEqualTo("SUCCESS");
    assertThat(captor.getValue().getCandidateCount()).isZero();
    assertThat(captor.getValue().getRetrievalStrategy()).isEqualTo("MYSQL_FALLBACK");
}
```

Add these test helpers:

```java
private InterviewRecord technicalRecord(Long id) {
    InterviewRecord record = new InterviewRecord();
    record.setId(id);
    record.setUserId(1L);
    record.setPosition("AI大模型");
    record.setPhase(InterviewPhase.TECHNICAL.name());
    return record;
}

private void stubStreamingResponse(String text) {
    doAnswer(invocation -> {
        StreamingResponseHandler<AiMessage> handler = invocation.getArgument(1);
        handler.onNext(text);
        handler.onComplete(Response.from(new AiMessage(text)));
        return null;
    }).when(streamingChatModel).generate(anyList(), any());
}
```

- [ ] **Step 3: Update the existing failure test to verify failed request logging**

Update existing `InterviewServiceImplTest` stubs that use `questionBankService.search(any())` so the runtime-path tests stub `questionBankService.searchWithMetadata(any())`. After the existing `verify(questionBankService)...` assertions, add:

```java
ArgumentCaptor<RagRetrievalRequestLog> requestLogCaptor =
        ArgumentCaptor.forClass(RagRetrievalRequestLog.class);
verify(ragRetrievalRequestLogMapper).insert(requestLogCaptor.capture());
assertThat(requestLogCaptor.getValue().getStatus()).isEqualTo("FAILED");
assertThat(requestLogCaptor.getValue().getCandidateCount()).isZero();
assertThat(requestLogCaptor.getValue().getErrorMessage()).contains("未配置岗位对应的知识库分类");
```

- [ ] **Step 4: Run the focused test and verify it fails**

Run:

```powershell
cd backend
mvn -Dtest=InterviewServiceImplTest test
```

Expected: FAIL because request logs are not inserted and `searchWithMetadata` is not called.

- [ ] **Step 5: Inject the request log mapper and use metadata-bearing search**

Add imports:

```java
import com.interview.dto.questionbank.QuestionBankSearchResponse;
import com.interview.entity.RagRetrievalRequestLog;
import com.interview.mapper.RagRetrievalRequestLogMapper;
import java.util.UUID;
```

Add the field:

```java
@Autowired
private RagRetrievalRequestLogMapper ragRetrievalRequestLogMapper;
```

Replace the search block with logic equivalent to:

```java
String requestId = UUID.randomUUID().toString();
long retrievalStartedAt = System.nanoTime();
QuestionBankSearchResponse searchResponse = null;
List<QuestionBankSearchResult> retrievedResults;
try {
    searchResponse = searchRequest != null
            ? questionBankService.searchWithMetadata(searchRequest)
            : QuestionBankSearchResponse.builder().results(List.of()).strategy("SKIPPED").build();
    retrievedResults = searchResponse.getResults();
    insertRequestLog(requestId, userId, recordId, position, nextPhase, ragQuery,
            searchRequest, retrievedResults.size(), searchResponse.getStrategy(),
            elapsedMillis(retrievalStartedAt), "SUCCESS", null);
} catch (Exception e) {
    log.warn("RAG 检索失败，跳过题库上下文: recordId={}, position={}, error={}",
            recordId, position, e.getMessage());
    recordSystemEvent(userId, "RAG_RETRIEVAL_FAILED", "system",
            Map.of("recordId", recordId, "position", position), false, e.getMessage());
    insertRequestLog(requestId, userId, recordId, position, nextPhase, ragQuery,
            searchRequest, 0, "FAILED", elapsedMillis(retrievalStartedAt), "FAILED", sanitizeError(e));
    retrievedResults = List.of();
}
```

When creating each `RagRetrievalLog`, add:

```java
logEntry.setRequestId(requestId);
```

Add helper methods:

```java
private long elapsedMillis(long startedAt) {
    return Math.max(0L, (System.nanoTime() - startedAt) / 1_000_000L);
}

private void insertRequestLog(String requestId, Long userId, Long recordId, String position,
                              InterviewPhase phase, String queryText, QuestionBankSearchRequest request,
                              int candidateCount, String strategy, long latencyMs,
                              String status, String errorMessage) {
    RagRetrievalRequestLog entry = new RagRetrievalRequestLog();
    entry.setRequestId(requestId);
    entry.setUserId(userId);
    entry.setRecordId(recordId);
    entry.setTurnIndex(null);
    entry.setPosition(position);
    entry.setPhase(phase != null ? phase.name() : null);
    entry.setQueryText(truncate(queryText, 500));
    entry.setRequestedLimit(request != null ? request.getLimit() : 0);
    entry.setCandidateCount(candidateCount);
    entry.setRetrievalStrategy(strategy);
    entry.setLatencyMs(latencyMs);
    entry.setStatus(status);
    entry.setErrorMessage(sanitizeError(errorMessage));
    try {
        ragRetrievalRequestLogMapper.insert(entry);
    } catch (Exception e) {
        log.warn("RAG 请求日志写入失败: {}", e.getMessage());
    }
}

private String truncate(String value, int maxLength) {
    if (value == null || value.length() <= maxLength) return value;
    return value.substring(0, maxLength);
}

private String sanitizeError(Exception error) {
    return sanitizeError(error != null ? error.getMessage() : null);
}

private String sanitizeError(String value) {
    if (value == null) return null;
    String sanitized = value
            .replaceAll("(?i)(api[_-]?key|token|password|secret)\\s*[=:]\\s*\\S+", "$1=[REDACTED]")
            .replaceAll("(?i)https?://\\S+", "[URL]");
    return truncate(sanitized, 500);
}
```

Before calling `insertRequestLog`, calculate `turnIdx` once and pass it into the helper instead of setting `null`. Move:

```java
int turnIdx = chatHistory.size() / 2 + 1;
```

above the search block, add `int turnIndex` to the helper signature, and set:

```java
entry.setTurnIndex(turnIndex);
```

- [ ] **Step 6: Run the focused tests**

Run:

```powershell
cd backend
mvn -Dtest=InterviewServiceImplTest,QuestionBankServiceSearchTest test
```

Expected: PASS.

- [ ] **Step 7: Run the full backend test suite**

Run:

```powershell
cd backend
mvn test
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 8: Commit request logging**

```bash
git add backend/src/main/java/com/interview/service/impl/InterviewServiceImpl.java backend/src/test/java/com/interview/service/InterviewServiceImplTest.java
git commit -m "feat: log every rag retrieval request"
```

## Task 5: Document Retrieval Logging

**Files:**
- Modify: `README.md`
- Modify: `CHANGELOG.md`

- [ ] **Step 1: Update README**

In the “题库与 Qdrant” section, add:

```markdown
### RAG 检索日志

面试技术阶段和 HR 阶段的题库检索会记录两层日志：

- `rag_retrieval_request_log`：每次检索请求一行，包含零命中和失败请求，用于分析召回覆盖与延迟。
- `rag_retrieval_log`：每个命中的知识原子一行，通过 `request_id` 关联请求级日志，用于分析相似度与召回排名。

`query_text` 可能包含候选人回答内容，只允许受限访问；导出评测集前必须脱敏，不能把用户 ID、记录 ID 或完整原始面试记录提交到 Git。
```

- [ ] **Step 2: Update CHANGELOG**

Under `## 未发布` -> `### 新增`, add:

```markdown
- 新增请求级 RAG 检索日志，记录零命中、失败、检索策略、候选数量与延迟，并通过 `request_id` 关联命中原子日志。
```

- [ ] **Step 3: Verify docs formatting**

Run:

```powershell
git diff --check
```

Expected: no output.

- [ ] **Step 4: Commit documentation**

```bash
git add README.md CHANGELOG.md
git commit -m "docs: describe rag retrieval request logging"
```

## Task 6: Final Verification

**Files:**
- Verify all files changed by this plan.

- [ ] **Step 1: Run backend tests**

```powershell
cd backend
mvn test
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 2: Verify migration and worktree**

```powershell
git diff --check
git status --short --branch
git log --oneline --decorate --max-count=8
```

Expected: no diff errors, clean worktree, and frequent commits for schema, types, service behavior, logging, and docs.

- [ ] **Step 3: Optional live verification after deployment**

After deploying to a test or production-like environment:

```sql
SELECT request_id, position, phase, candidate_count, retrieval_strategy, status, latency_ms
FROM rag_retrieval_request_log
ORDER BY id DESC
LIMIT 20;

SELECT request_id, retrieved_atom_id, similarity_score, rank_index
FROM rag_retrieval_log
WHERE request_id IS NOT NULL
ORDER BY id DESC
LIMIT 20;
```

Expected: zero-hit requests appear in the request table, and hit rows share the same `request_id`.

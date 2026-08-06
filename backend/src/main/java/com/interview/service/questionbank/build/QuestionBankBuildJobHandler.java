package com.interview.service.questionbank.build;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.interview.config.QuestionBankBuildProperties;
import com.interview.entity.AppJob;
import com.interview.entity.KnowledgeSourceFile;
import com.interview.entity.QuestionBankBuild;
import com.interview.entity.QuestionBankBuildCandidate;
import com.interview.mapper.KnowledgeSourceFileMapper;
import com.interview.mapper.QuestionBankBuildCandidateMapper;
import com.interview.mapper.QuestionBankBuildMapper;
import com.interview.service.AppJobHandler;
import com.interview.service.AppJobService;
import com.interview.service.UserLlmConfigService;
import com.interview.service.UserLlmRuntimeConfig;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class QuestionBankBuildJobHandler implements AppJobHandler {
    private final QuestionBankBuildMapper buildMapper;
    private final QuestionBankBuildCandidateMapper candidateMapper;
    private final KnowledgeSourceFileMapper sourceFileMapper;
    private final QuestionBankBuildFileStorage storage;
    private final QuestionBankBuildProperties properties;
    private final UserLlmConfigService userLlmConfigService;
    private final QuestionBankBuildLlm llm;
    private final AppJobService appJobService;

    public QuestionBankBuildJobHandler(QuestionBankBuildMapper buildMapper,
                                       QuestionBankBuildCandidateMapper candidateMapper,
                                       KnowledgeSourceFileMapper sourceFileMapper,
                                       QuestionBankBuildFileStorage storage,
                                       QuestionBankBuildProperties properties,
                                       UserLlmConfigService userLlmConfigService,
                                       QuestionBankBuildLlm llm,
                                       AppJobService appJobService) {
        this.buildMapper = buildMapper;
        this.candidateMapper = candidateMapper;
        this.sourceFileMapper = sourceFileMapper;
        this.storage = storage;
        this.properties = properties;
        this.userLlmConfigService = userLlmConfigService;
        this.llm = llm;
        this.appJobService = appJobService;
    }

    @Override
    public String jobType() {
        return QuestionBankBuildService.JOB_TYPE;
    }

    @Override
    public void handle(AppJob job) {
        Long buildId = job.getBuildId();
        if (buildId == null) throw new IllegalArgumentException("题库构建作业缺少 buildId");
        QuestionBankBuild build = buildMapper.selectById(buildId);
        if (build == null || !QuestionBankBuildService.SCOPE_PRIVATE.equalsIgnoreCase(build.getScope())) {
            throw new IllegalArgumentException("题库构建不存在");
        }
        try {
            setBuildRunning(build);
            UserLlmRuntimeConfig runtime = userLlmConfigService.requireOwnedRuntimeConfig(build.getOwnerUserId(), build.getLlmConfigId());
            List<ChunkRef> chunks = loadChunks(build);
            if (chunks.size() > properties.getMaxChunks()) throw new IllegalArgumentException("分块数量超过上限");
            Set<Integer> completed = parseCheckpoint(build.getCheckpointJson());
            for (ChunkRef chunk : chunks) {
                if (completed.contains(chunk.globalIndex())) continue;
                ensureBuildExecutable(build.getId());
                processChunk(build, chunk, runtime);
                completed.add(chunk.globalIndex());
                persistCheckpoint(build, completed, chunks.size());
                int progress = Math.min(99, (int) Math.round(completed.size() * 100.0 / Math.max(1, chunks.size())));
                appJobService.updateRunningJob(job.getId(), job.getClaimedBy(), "GENERATING", progress);
            }
            build.setStatus(AppJobService.STATUS_COMPLETED);
            build.setStage("READY_FOR_REVIEW");
            build.setProgress(100);
            build.setCompletedChunkCount(completed.size());
            build.setErrorMessage(null);
            buildMapper.updateById(build);
            job.setResultJson(JSON.toJSONString(Map.of("buildId", build.getId(), "candidateCount", build.getCandidateCount() == null ? 0 : build.getCandidateCount(), "chunkCount", chunks.size())));
        } catch (RuntimeException e) {
            markBuildFailed(build, sanitize(e.getMessage()));
            throw e;
        }
    }

    protected void processChunk(QuestionBankBuild build, ChunkRef chunk, UserLlmRuntimeConfig runtime) {
        List<QuestionBankBuildCandidate> existingCandidates = candidateMapper.selectList(new QueryWrapper<QuestionBankBuildCandidate>()
                .eq("build_id", build.getId()).eq("chunk_index", chunk.globalIndex()).orderByAsc("id"));
        Integer expectedCandidates = expectedCandidateCount(existingCandidates);
        if (!existingCandidates.isEmpty()) {
            if (expectedCandidates != null && existingCandidates.size() < expectedCandidates) {
                throw new IllegalStateException("分块候选只落库了部分内容，为避免模型重排造成错题，请删除该构建后重新发起");
            }
            completeMissingSelfChecks(build, chunk, runtime);
            refreshBuildCounts(build);
            return;
        }
        int existingCount = candidateCount(build.getId());
        if (existingCount >= properties.getMaxCandidates()) throw new IllegalArgumentException("候选原子数量超过上限");
        String systemPrompt = systemPrompt(build);
        String userPrompt = "请从以下文档片段生成一个或多个可用于技术面试的知识原子。\n"
                + "文档来源：" + chunk.sourceFile().getOriginalFilename() + "，片段序号：" + chunk.localIndex() + "\n"
                + "文档片段：\n" + chunk.text();
        String generation = llm.complete(runtime, systemPrompt, userPrompt);
        List<JSONObject> atoms = parseAtoms(generation);
        if (atoms.isEmpty()) throw new IllegalStateException("模型未返回知识原子 JSON");
        int requiredChunkCandidates = Math.max(
                expectedCandidates == null ? 0 : expectedCandidates,
                atoms.size());
        int persistedCount = existingCount;
        for (int ordinal = 0; ordinal < atoms.size(); ordinal++) {
            JSONObject atom = atoms.get(ordinal);
            QuestionBankBuildCandidate candidate = toCandidate(build, chunk, atom, ordinal);
            QuestionBankBuildCandidate existing = findByStableId(candidate);
            if (existing != null) continue;
            if (persistedCount >= properties.getMaxCandidates()) {
                throw new IllegalArgumentException("候选原子数量超过上限");
            }
            candidate.setSelfCheckJson(pendingSelfCheck(requiredChunkCandidates));
            // Persist the generated content before any self-check call. If a later
            // self-check fails, retry sees the candidate and never regenerates it.
            candidateMapper.insert(candidate);
            persistedCount++;
        }
        int persistedChunkCandidates = candidateMapper.selectList(new QueryWrapper<QuestionBankBuildCandidate>()
                .eq("build_id", build.getId()).eq("chunk_index", chunk.globalIndex())).size();
        if (persistedChunkCandidates < requiredChunkCandidates) {
            throw new IllegalStateException("分块候选恢复不完整，请重试原任务");
        }
        refreshBuildCounts(build);
        completeMissingSelfChecks(build, chunk, runtime);
        refreshBuildCounts(build);
    }

    private void completeMissingSelfChecks(QuestionBankBuild build,
                                           ChunkRef chunk,
                                           UserLlmRuntimeConfig runtime) {
        List<QuestionBankBuildCandidate> candidates = candidateMapper.selectList(new QueryWrapper<QuestionBankBuildCandidate>()
                .eq("build_id", build.getId()).eq("chunk_index", chunk.globalIndex()).orderByAsc("id"));
        for (QuestionBankBuildCandidate candidate : candidates) {
            if (!needsSelfCheck(candidate.getSelfCheckJson())) continue;
            String selfCheck = llm.complete(runtime, selfCheckSystemPrompt(), selfCheckPrompt(candidate));
            JSONObject check = parseObject(selfCheck);
            if (check.getBoolean("passed") == null) {
                throw new IllegalStateException("模型自检结果缺少 passed 字段");
            }
            candidate.setSelfCheckJson(JSON.toJSONString(check));
            candidate.setDuplicateHint(check.getString("duplicateHint"));
            QuestionBankBuildCandidate update = new QuestionBankBuildCandidate();
            update.setId(candidate.getId());
            update.setSelfCheckJson(candidate.getSelfCheckJson());
            update.setDuplicateHint(candidate.getDuplicateHint());
            candidateMapper.updateById(update);
        }
    }

    private String pendingSelfCheck(int expectedCandidates) {
        return JSON.toJSONString(Map.of(
                "status", "PENDING",
                "expectedChunkCandidates", expectedCandidates));
    }

    private Integer expectedCandidateCount(List<QuestionBankBuildCandidate> candidates) {
        for (QuestionBankBuildCandidate candidate : candidates) {
            String value = candidate.getSelfCheckJson();
            if (value == null || value.isBlank()) continue;
            try {
                JSONObject check = JSON.parseObject(value);
                Integer expected = check.getInteger("expectedChunkCandidates");
                if (expected != null && expected > 0) return expected;
            } catch (Exception ignored) {
                // Invalid self-check JSON is handled by the review/import validation path.
            }
        }
        return null;
    }

    private boolean needsSelfCheck(String value) {
        if (value == null || value.isBlank()) return true;
        try {
            JSONObject check = JSON.parseObject(value);
            return "PENDING".equalsIgnoreCase(check.getString("status"))
                    || check.getBoolean("passed") == null;
        } catch (Exception e) {
            return true;
        }
    }

    private QuestionBankBuildCandidate findByStableId(QuestionBankBuildCandidate candidate) {
        return candidateMapper.selectOne(new QueryWrapper<QuestionBankBuildCandidate>()
                .eq("build_id", candidate.getBuildId())
                .eq("stable_atom_id", candidate.getStableAtomId())
                .last("LIMIT 1"));
    }

    private List<ChunkRef> loadChunks(QuestionBankBuild build) {
        List<ChunkRef> chunks = new ArrayList<>();
        List<KnowledgeSourceFile> files = sourceFileMapper.selectList(new QueryWrapper<KnowledgeSourceFile>()
                .eq("build_id", build.getId()).orderByAsc("id"));
        int global = 0;
        for (KnowledgeSourceFile file : files) {
            try {
                String text = storage.readText(file.getMarkdownStorageKey());
                List<String> fileChunks = QuestionBankBuildChunker.split(text, properties.getChunkChars(), properties.getChunkOverlapChars());
                for (int local = 0; local < fileChunks.size(); local++) {
                    chunks.add(new ChunkRef(global++, local, file, fileChunks.get(local)));
                }
            } catch (Exception e) {
                throw new IllegalStateException("源文件文本读取失败");
            }
        }
        return chunks;
    }

    private QuestionBankBuildCandidate toCandidate(QuestionBankBuild build, ChunkRef chunk, JSONObject atom, int ordinal) {
        JSONObject content = atom.getJSONObject("content");
        String subject = firstNonBlank(atom.getString("subject"), atom.getString("question"));
        String category = firstNonBlank(atom.getString("category"), firstCategory(build));
        String difficulty = firstNonBlank(atom.getString("difficulty"), "mid");
        String principles = firstNonBlank(content == null ? null : content.getString("principles"), atom.getString("principles"));
        String pitfalls = firstNonBlank(content == null ? null : content.getString("pitfalls"), atom.getString("pitfalls"));
        List<String> followUps = parseStringList(content == null ? null : content.get("followUpPaths"));
        if (followUps.isEmpty()) followUps = parseStringList(content == null ? null : content.get("follow_up_paths"));
        if (followUps.isEmpty()) followUps = parseStringList(atom.get("followUpPaths"));
        if (followUps.isEmpty()) followUps = parseStringList(atom.get("follow_up_paths"));
        List<String> tags = parseStringList(atom.get("tags"));
        if (blank(subject) || blank(principles) || followUps.size() < 2) {
            throw new IllegalStateException("模型原子缺少必填字段或追问路径");
        }
        Object evidenceValue = atom.get("sourceEvidence");
        if (evidenceValue == null) evidenceValue = atom.get("source_evidence");
        JSONArray evidence = normalizeSourceEvidence(evidenceValue, chunk);
        QuestionBankBuildCandidate candidate = new QuestionBankBuildCandidate();
        candidate.setBuildId(build.getId()); candidate.setOwnerUserId(build.getOwnerUserId()); candidate.setPositionId(build.getPositionId()); candidate.setKnowledgeBaseId(build.getKnowledgeBaseId());
        candidate.setSourceFileId(chunk.sourceFile().getId()); candidate.setChunkIndex(chunk.globalIndex());
        candidate.setStableAtomId(stableAtomId(
                chunk.sourceFile().getId(), chunk.sourceFile().getFileHash(), chunk.localIndex(), ordinal));
        candidate.setSourceRef(chunk.sourceFile().getOriginalFilename() + "#chunk-" + chunk.localIndex());
        candidate.setSubject(subject.trim()); candidate.setCategory(category.trim()); candidate.setDifficulty(difficulty.trim()); candidate.setTagsJson(JSON.toJSONString(tags));
        candidate.setPrinciples(principles.trim()); candidate.setPitfalls(pitfalls == null ? "" : pitfalls.trim()); candidate.setFollowUpPathsJson(JSON.toJSONString(followUps));
        candidate.setSourceEvidenceJson(evidence.toJSONString()); candidate.setReviewStatus("PENDING");
        return candidate;
    }

    private List<String> parseStringList(Object raw) {
        if (raw instanceof JSONArray array) {
            return array.stream().map(String::valueOf).map(String::trim).filter(value -> !value.isBlank()).toList();
        }
        if (raw instanceof List<?> list) {
            return list.stream().map(String::valueOf).map(String::trim).filter(value -> !value.isBlank()).toList();
        }
        if (raw instanceof String text && !text.isBlank()) {
            String trimmed = text.trim();
            try {
                if (trimmed.startsWith("[")) return JSON.parseArray(trimmed, String.class).stream().map(String::trim).filter(value -> !value.isBlank()).toList();
            } catch (Exception ignored) { }
            return List.of(trimmed.split("\\R")).stream().map(String::trim).filter(value -> !value.isBlank()).toList();
        }
        return List.of();
    }

    private JSONArray normalizeSourceEvidence(Object raw, ChunkRef chunk) {
        JSONArray values = new JSONArray();
        if (raw instanceof JSONArray array) {
            values.addAll(array);
        } else if (raw instanceof Map<?, ?>) {
            values.add(raw);
        } else if (raw instanceof String text && !text.isBlank()) {
            String trimmed = text.trim();
            try {
                if (trimmed.startsWith("[")) values.addAll(JSON.parseArray(trimmed));
                else if (trimmed.startsWith("{")) values.add(JSON.parseObject(trimmed));
                else values.add(trimmed);
            } catch (Exception ignored) {
                values.add(trimmed);
            }
        }

        JSONArray result = new JSONArray();
        for (Object value : values) {
            String quote;
            String pageOrSection = null;
            if (value instanceof Map<?, ?> map) {
                Object quoteValue = map.get("quote");
                quote = quoteValue == null ? null : String.valueOf(quoteValue);
                Object pageValue = map.get("pageOrSection");
                if (pageValue == null) pageValue = map.get("page_or_section");
                pageOrSection = pageValue == null ? null : String.valueOf(pageValue);
            } else {
                quote = value == null ? null : String.valueOf(value);
            }
            if (blank(quote)) continue;
            JSONObject item = new JSONObject();
            item.put("quote", truncate(quote.trim(), 500));
            item.put("pageOrSection", blank(pageOrSection) ? "chunk-" + chunk.localIndex() : pageOrSection.trim());
            result.add(item);
        }
        if (result.isEmpty()) {
            result.add(Map.of("quote", truncate(chunk.text(), 500), "pageOrSection", "chunk-" + chunk.localIndex()));
        }
        return result;
    }

    private void persistCheckpoint(QuestionBankBuild build, Set<Integer> completed, int chunkCount) {
        build.setCheckpointJson(JSON.toJSONString(completed.stream().sorted().toList()));
        build.setCompletedChunkCount(completed.size());
        build.setChunkCount(chunkCount);
        buildMapper.updateById(build);
    }

    private void refreshBuildCounts(QuestionBankBuild build) {
        int count = candidateCount(build.getId());
        int accepted = Math.toIntExact(candidateMapper.selectCount(new QueryWrapper<QuestionBankBuildCandidate>().eq("build_id", build.getId()).eq("review_status", "ACCEPTED")));
        int rejected = Math.toIntExact(candidateMapper.selectCount(new QueryWrapper<QuestionBankBuildCandidate>().eq("build_id", build.getId()).eq("review_status", "REJECTED")));
        build.setCandidateCount(count); build.setAcceptedCount(accepted); build.setRejectedCount(rejected); buildMapper.updateById(build);
    }

    private int candidateCount(Long buildId) {
        return Math.toIntExact(candidateMapper.selectCount(new QueryWrapper<QuestionBankBuildCandidate>().eq("build_id", buildId)));
    }

    private void setBuildRunning(QuestionBankBuild build) {
        int progress = Math.max(0, build.getProgress() == null ? 0 : build.getProgress());
        int updated = buildMapper.update(null, new UpdateWrapper<QuestionBankBuild>()
                .eq("id", build.getId())
                .in("status", AppJobService.STATUS_PENDING, AppJobService.STATUS_FAILED)
                .set("status", AppJobService.STATUS_RUNNING)
                .set("stage", "GENERATING")
                .set("progress", progress)
                .set("error_message", null));
        if (updated != 1) throw new IllegalStateException("题库构建状态已变化，任务不能继续执行");
        build.setStatus(AppJobService.STATUS_RUNNING); build.setStage("GENERATING"); build.setProgress(progress); build.setErrorMessage(null);
    }

    private void markBuildFailed(QuestionBankBuild build, String message) {
        int updated = buildMapper.update(null, new UpdateWrapper<QuestionBankBuild>()
                .eq("id", build.getId())
                .eq("status", AppJobService.STATUS_RUNNING)
                .set("status", AppJobService.STATUS_FAILED)
                .set("stage", "FAILED")
                .set("progress", 100)
                .set("error_message", message));
        if (updated == 1) {
            build.setStatus(AppJobService.STATUS_FAILED); build.setStage("FAILED"); build.setProgress(100); build.setErrorMessage(message);
        }
    }

    private Set<Integer> parseCheckpoint(String json) {
        if (json == null || json.isBlank()) return new HashSet<>();
        try { return new HashSet<>(JSON.parseArray(json, Integer.class)); } catch (Exception e) { return new HashSet<>(); }
    }

    private List<JSONObject> parseAtoms(String raw) {
        String value = stripMarkdown(raw);
        try {
            if (value.startsWith("[")) return JSON.parseArray(value).toJavaList(JSONObject.class);
            JSONObject object = JSON.parseObject(value);
            JSONArray atoms = object.getJSONArray("atoms");
            return atoms == null ? List.of(object) : atoms.toJavaList(JSONObject.class);
        } catch (Exception e) {
            throw new IllegalStateException("模型返回不是有效 JSON");
        }
    }

    private JSONObject parseObject(String raw) {
        try { return JSON.parseObject(stripMarkdown(raw)); } catch (Exception e) { throw new IllegalStateException("模型自检返回不是有效 JSON"); }
    }

    private String systemPrompt(QuestionBankBuild build) {
        return "你是题库构建 Agent。严格输出纯 JSON，不要 Markdown。输出结构必须为："
                + "{\"atoms\":[{\"subject\":\"考点\",\"category\":\"分类\",\"difficulty\":\"junior|mid|senior|principal\",\"tags\":[],"
                + "\"content\":{\"principles\":\"核心原理\",\"pitfalls\":\"常见误区\",\"followUpPaths\":[\"深入追问\",\"引导追问\"]},"
                + "\"sourceEvidence\":[{\"quote\":\"文档原句\",\"pageOrSection\":\"页码或章节\"}]}]}。"
                + "followUpPaths 必须至少包含一个深入追问和一个引导追问；只依据文档，不得编造；category 可选范围：" + build.getCategoriesJson();
    }

    private String selfCheckSystemPrompt() {
        return "你是题库质量自检器。严格输出纯 JSON：{\"passed\":true|false,\"reason\":\"...\",\"confidence\":0.0,\"duplicateHint\":\"...\"}。不要输出思维过程。";
    }

    private String selfCheckPrompt(QuestionBankBuildCandidate candidate) {
        JSONObject payload = new JSONObject();
        payload.put("subject", candidate.getSubject());
        payload.put("category", candidate.getCategory());
        payload.put("principles", candidate.getPrinciples());
        payload.put("pitfalls", candidate.getPitfalls());
        payload.put("followUpPaths", JSON.parseArray(candidate.getFollowUpPathsJson()));
        payload.put("sourceEvidence", JSON.parseArray(candidate.getSourceEvidenceJson()));
        return "请审核以下候选是否符合一个清晰面试知识点、答案可评估、包含深入和引导追问、来源可追溯，并指出疑似重复：\n"
                + payload.toJSONString();
    }

    static String stableAtomId(Long sourceFileId, String fileHash, int localIndex, int ordinal) {
        String raw = sourceFileId + "|" + String.valueOf(fileHash) + "|" + localIndex + "|" + ordinal;
        try { return "generated-" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8))).substring(0, 28); }
        catch (Exception e) { throw new IllegalStateException("稳定原子 ID 生成失败", e); }
    }

    private void ensureBuildExecutable(Long buildId) {
        QuestionBankBuild current = buildMapper.selectById(buildId);
        if (current == null || !QuestionBankBuildService.SCOPE_PRIVATE.equalsIgnoreCase(current.getScope())
                || !AppJobService.STATUS_RUNNING.equalsIgnoreCase(current.getStatus())) {
            throw new IllegalStateException("题库构建已不存在或不允许继续执行");
        }
    }

    private String firstCategory(QuestionBankBuild build) {
        try { List<String> list = JSON.parseArray(build.getCategoriesJson(), String.class); return list.isEmpty() ? "通用" : list.get(0); }
        catch (Exception e) { return "通用"; }
    }

    private String stripMarkdown(String raw) {
        if (raw == null) return "";
        String value = raw.trim();
        if (value.startsWith("```") && value.endsWith("```")) {
            int newline = value.indexOf('\n');
            value = newline >= 0 ? value.substring(newline + 1, value.length() - 3) : value.substring(3, value.length() - 3);
        }
        return value.trim();
    }

    private String sanitize(String message) {
        if (message == null || message.isBlank()) return "题库构建失败";
        String sanitized = message.replaceAll("(?i)(Authorization\\s*[:=]\\s*\\S+|Bearer\\s+\\S+|api_key\\s*[:=]\\s*\\S+|sk-[A-Za-z0-9_-]+)", "[REDACTED]");
        return sanitized.substring(0, Math.min(300, sanitized.length()));
    }

    private String truncate(String value, int max) { return value == null || value.length() <= max ? value : value.substring(0, max); }
    private boolean blank(String value) { return value == null || value.isBlank(); }
    private String firstNonBlank(String first, String fallback) { return blank(first) ? fallback : first; }

    private record ChunkRef(int globalIndex, int localIndex, KnowledgeSourceFile sourceFile, String text) {
    }
}

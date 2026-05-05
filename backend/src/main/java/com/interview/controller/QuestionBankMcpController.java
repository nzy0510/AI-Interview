package com.interview.controller;

import com.alibaba.fastjson2.JSON;
import com.interview.dto.questionbank.QuestionBankImportRequest;
import com.interview.dto.questionbank.QuestionBankImportResult;
import com.interview.dto.questionbank.QuestionBankSearchRequest;
import com.interview.dto.questionbank.QuestionBankSearchResult;
import com.interview.entity.KnowledgeAtom;
import com.interview.service.questionbank.QuestionBankService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/mcp")
public class QuestionBankMcpController {

    private static final String PROTOCOL_VERSION = "2025-11-25";

    private final QuestionBankService questionBankService;

    @Value("${question-bank.admin-token:}")
    private String adminToken;

    @Value("${question-bank.mcp.read-token:}")
    private String readToken;

    @Value("${question-bank.mcp.allowed-origins:}")
    private String allowedOrigins;

    public QuestionBankMcpController(QuestionBankService questionBankService) {
        this.questionBankService = questionBankService;
    }

    @PostMapping
    public ResponseEntity<Object> handle(@RequestBody Map<String, Object> request,
                                         HttpServletRequest servletRequest) {
        Object id = request.get("id");
        if (!isAllowedOrigin(servletRequest)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(jsonRpcError(id, -32003, "MCP origin is not allowed"));
        }
        if (id == null) {
            return ResponseEntity.accepted().build();
        }

        String method = stringValue(request.get("method"));
        Map<String, Object> params = asMap(request.get("params"));
        try {
            Object result = dispatch(method, params, servletRequest);
            return ResponseEntity.ok(jsonRpcResult(id, result));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(jsonRpcError(id, -32001, e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.ok(jsonRpcError(id, -32602, e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.ok(jsonRpcError(id, -32603, e.getMessage()));
        }
    }

    private Object dispatch(String method, Map<String, Object> params, HttpServletRequest request) {
        return switch (method) {
            case "initialize" -> initialize();
            case "tools/list" -> {
                requireRead(request);
                yield Map.of("tools", tools());
            }
            case "tools/call" -> {
                String name = stringValue(params.get("name"));
                Map<String, Object> arguments = asMap(params.get("arguments"));
                yield callTool(name, arguments, request);
            }
            case "resources/list" -> {
                requireRead(request);
                yield Map.of("resources", resources());
            }
            case "resources/read" -> {
                requireRead(request);
                yield readResource(stringValue(params.get("uri")));
            }
            case "prompts/list" -> {
                requireRead(request);
                yield Map.of("prompts", prompts());
            }
            case "prompts/get" -> {
                requireRead(request);
                yield getPrompt(stringValue(params.get("name")), asMap(params.get("arguments")));
            }
            default -> throw new IllegalArgumentException("Unsupported MCP method: " + method);
        };
    }

    private Map<String, Object> initialize() {
        return Map.of(
                "protocolVersion", PROTOCOL_VERSION,
                "capabilities", Map.of(
                        "tools", Map.of("listChanged", false),
                        "resources", Map.of("subscribe", false, "listChanged", false),
                        "prompts", Map.of("listChanged", false)
                ),
                "serverInfo", Map.of(
                        "name", "ai-interview-question-bank",
                        "version", "1.0.0"
                )
        );
    }

    private Map<String, Object> callTool(String name, Map<String, Object> arguments, HttpServletRequest request) {
        return switch (name) {
            case "search_interview_atoms" -> {
                requireRead(request);
                QuestionBankSearchRequest searchRequest = parse(arguments, QuestionBankSearchRequest.class);
                yield toolResult(questionBankService.search(searchRequest));
            }
            case "get_interview_atom" -> {
                requireRead(request);
                yield toolResult(questionBankService.getByAtomId(requiredString(arguments, "atomId")));
            }
            case "list_interview_categories" -> {
                requireRead(request);
                yield toolResult(questionBankService.listCategories());
            }
            case "generate_interview_context" -> {
                requireRead(request);
                QuestionBankSearchRequest searchRequest = parse(arguments, QuestionBankSearchRequest.class);
                List<QuestionBankSearchResult> results = questionBankService.search(searchRequest);
                yield toolResult(Map.of("context", buildContext(results), "hits", results));
            }
            case "validate_atom_import_package" -> {
                requireAdmin(request);
                QuestionBankImportRequest importRequest = importRequest(arguments);
                yield toolResult(Map.of("errors", questionBankService.validateImportPackage(importRequest)));
            }
            case "submit_atom_import_package" -> {
                requireAdmin(request);
                QuestionBankImportRequest importRequest = importRequest(arguments);
                QuestionBankImportResult result = questionBankService.importBatch(importRequest);
                yield toolResult(result);
            }
            case "reindex_question_bank" -> {
                requireAdmin(request);
                yield toolResult(Map.of("synced", questionBankService.reindexPublishedAtoms()));
            }
            default -> throw new IllegalArgumentException("Unsupported MCP tool: " + name);
        };
    }

    private String buildContext(List<QuestionBankSearchResult> results) {
        StringBuilder context = new StringBuilder();
        for (int i = 0; i < results.size(); i++) {
            QuestionBankSearchResult result = results.get(i);
            context.append(i + 1).append(". [atom_id: ")
                    .append(result.getAtomId())
                    .append("]\n")
                    .append(result.getPromptContext())
                    .append("\n");
        }
        return context.toString();
    }

    private Map<String, Object> readResource(String uri) {
        if ("schema://knowledge-atom".equals(uri)) {
            return Map.of("contents", List.of(Map.of(
                    "uri", uri,
                    "mimeType", "application/json",
                    "text", JSON.toJSONString(atomSchema())
            )));
        }
        if (uri != null && uri.startsWith("atom://")) {
            String atomId = uri.substring("atom://".length());
            KnowledgeAtom atom = questionBankService.getByAtomId(atomId);
            if (atom == null) throw new IllegalArgumentException("Atom not found: " + atomId);
            return Map.of("contents", List.of(Map.of(
                    "uri", uri,
                    "mimeType", "application/json",
                    "text", JSON.toJSONString(atom)
            )));
        }
        throw new IllegalArgumentException("Unsupported resource uri: " + uri);
    }

    private Map<String, Object> getPrompt(String name, Map<String, Object> arguments) {
        if (!"interview-followup-context".equals(name)) {
            throw new IllegalArgumentException("Unsupported prompt: " + name);
        }
        String position = stringValue(arguments.getOrDefault("position", "java"));
        String query = stringValue(arguments.getOrDefault("query", ""));
        return Map.of(
                "description", "Generate a compact technical-interview follow-up context from the question bank.",
                "messages", List.of(Map.of(
                        "role", "user",
                        "content", Map.of(
                                "type", "text",
                                "text", "请基于岗位 " + position + " 和候选回答检索题库，并输出可追问的知识点上下文。候选回答：" + query
                        )
                ))
        );
    }

    private List<Map<String, Object>> tools() {
        List<Map<String, Object>> tools = new ArrayList<>();
        tools.add(tool("search_interview_atoms", "Search published interview knowledge atoms.",
                objectSchema(Map.of(
                        "position", stringSchema("Target interview position, for example java or 前端."),
                        "query", stringSchema("Candidate answer or interviewer question."),
                        "categories", arraySchema("Restrict search to these knowledge categories."),
                        "excludeAtomIds", arraySchema("Atom ids that should not be reused."),
                        "limit", numberSchema("Maximum hits, default 3.")
                ), List.of("query"))));
        tools.add(tool("get_interview_atom", "Read one interview knowledge atom by atom id.",
                objectSchema(Map.of("atomId", stringSchema("Knowledge atom id.")), List.of("atomId"))));
        tools.add(tool("list_interview_categories", "List question-bank categories and status counts.",
                objectSchema(Map.of(), List.of())));
        tools.add(tool("generate_interview_context", "Search atoms and format them as interviewer prompt context.",
                objectSchema(Map.of(
                        "position", stringSchema("Target interview position."),
                        "query", stringSchema("Candidate answer or interviewer question."),
                        "categories", arraySchema("Optional category filter."),
                        "excludeAtomIds", arraySchema("Optional used atom blacklist."),
                        "limit", numberSchema("Maximum hits, default 3.")
                ), List.of("query"))));
        tools.add(tool("validate_atom_import_package", "Validate a question-bank import package without writing atoms.",
                objectSchema(atomImportProperties(), List.of("atoms"))));
        tools.add(tool("submit_atom_import_package", "Import a question-bank package as DRAFT or AUTO_PUBLISH.",
                objectSchema(atomImportProperties(), List.of("atoms"))));
        tools.add(tool("reindex_question_bank", "Rebuild Qdrant vectors for all published atoms.",
                objectSchema(Map.of(), List.of())));
        return tools;
    }

    private List<Map<String, Object>> resources() {
        return List.of(Map.of(
                "uri", "schema://knowledge-atom",
                "name", "Knowledge atom import schema",
                "mimeType", "application/json",
                "description", "JSON schema used by submit_atom_import_package."
        ));
    }

    private List<Map<String, Object>> prompts() {
        return List.of(Map.of(
                "name", "interview-followup-context",
                "description", "Prompt for generating a follow-up context from interview question-bank retrieval.",
                "arguments", List.of(
                        Map.of("name", "position", "description", "Interview position.", "required", false),
                        Map.of("name", "query", "description", "Candidate answer or question.", "required", true)
                )
        ));
    }

    private Map<String, Object> atomSchema() {
        return Map.of(
                "type", "object",
                "required", List.of("id", "subject", "category", "content"),
                "properties", atomProperties()
        );
    }

    private Map<String, Object> atomImportProperties() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("batchId", stringSchema("Optional import batch id."));
        properties.put("sourceRef", stringSchema("Source file, document, or generation note."));
        properties.put("targetCategory", stringSchema("Default category for atoms without category."));
        properties.put("mode", stringSchema("DRY_RUN, DRAFT, or AUTO_PUBLISH."));
        properties.put("atoms", Map.of("type", "array", "items", atomSchema()));
        properties.put("validationReport", Map.of("type", "object"));
        properties.put("reviewReport", Map.of("type", "object"));
        return properties;
    }

    private Map<String, Object> atomProperties() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("id", stringSchema("Stable atom id, for example java-jvm-gc-001."));
        properties.put("subject", stringSchema("Knowledge point title."));
        properties.put("category", stringSchema("Question-bank category."));
        properties.put("difficulty", stringSchema("junior, mid, senior, or principal."));
        properties.put("tags", arraySchema("Search and management tags."));
        properties.put("sourceRef", stringSchema("Optional source reference."));
        properties.put("content", Map.of(
                "type", "object",
                "required", List.of("principles"),
                "properties", Map.of(
                        "principles", stringSchema("Core principles and expected answer."),
                        "pitfalls", stringSchema("Common mistakes or traps."),
                        "followUpPaths", arraySchema("Suggested follow-up question paths.")
                )
        ));
        return properties;
    }

    private Map<String, Object> tool(String name, String description, Map<String, Object> inputSchema) {
        return Map.of("name", name, "description", description, "inputSchema", inputSchema);
    }

    private Map<String, Object> objectSchema(Map<String, Object> properties, List<String> required) {
        return Map.of("type", "object", "properties", properties, "required", required);
    }

    private Map<String, Object> stringSchema(String description) {
        return Map.of("type", "string", "description", description);
    }

    private Map<String, Object> arraySchema(String description) {
        return Map.of("type", "array", "items", Map.of("type", "string"), "description", description);
    }

    private Map<String, Object> numberSchema(String description) {
        return Map.of("type", "number", "description", description);
    }

    private Map<String, Object> toolResult(Object value) {
        return Map.of(
                "content", List.of(Map.of("type", "text", "text", JSON.toJSONString(value))),
                "isError", false
        );
    }

    private QuestionBankImportRequest importRequest(Map<String, Object> arguments) {
        Object nestedPackage = arguments.get("package");
        if (nestedPackage instanceof Map<?, ?>) {
            return parse(nestedPackage, QuestionBankImportRequest.class);
        }
        return parse(arguments, QuestionBankImportRequest.class);
    }

    private <T> T parse(Object value, Class<T> type) {
        return JSON.parseObject(JSON.toJSONString(value), type);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return new LinkedHashMap<>();
    }

    private String requiredString(Map<String, Object> arguments, String key) {
        String value = stringValue(arguments.get(key));
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(key + " is required");
        }
        return value;
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private void requireRead(HttpServletRequest request) {
        String token = tokenFrom(request);
        boolean adminMatched = hasText(adminToken) && adminToken.equals(token);
        if (hasText(readToken) && !readToken.equals(token) && !adminMatched) {
            throw new SecurityException("MCP read token is invalid");
        }
    }

    private void requireAdmin(HttpServletRequest request) {
        String token = tokenFrom(request);
        if (hasText(adminToken) && !adminToken.equals(token)) {
            throw new SecurityException("Question-bank admin token is invalid");
        }
    }

    private String tokenFrom(HttpServletRequest request) {
        String token = request.getHeader("X-MCP-Token");
        if (!hasText(token)) token = request.getHeader("X-Question-Bank-Token");
        if (!hasText(token)) {
            String auth = request.getHeader("Authorization");
            if (auth != null && auth.startsWith("Bearer ")) {
                token = auth.substring(7);
            }
        }
        return token;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private boolean isAllowedOrigin(HttpServletRequest request) {
        String origin = request.getHeader("Origin");
        if (!hasText(origin)) return true;

        String configured = allowedOrigins != null ? allowedOrigins : "";
        List<String> configuredOrigins = Arrays.stream(configured.split(","))
                .map(String::trim)
                .filter(item -> !item.isBlank())
                .collect(Collectors.toList());
        if (!configuredOrigins.isEmpty()) {
            return configuredOrigins.contains(origin);
        }

        try {
            URI uri = URI.create(origin);
            String host = uri.getHost();
            return "localhost".equalsIgnoreCase(host)
                    || "127.0.0.1".equals(host)
                    || "::1".equals(host)
                    || request.getServerName().equalsIgnoreCase(host);
        } catch (Exception e) {
            return false;
        }
    }

    private Map<String, Object> jsonRpcResult(Object id, Object result) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("jsonrpc", "2.0");
        response.put("id", id);
        response.put("result", result);
        return response;
    }

    private Map<String, Object> jsonRpcError(Object id, int code, String message) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("jsonrpc", "2.0");
        response.put("id", id);
        response.put("error", Map.of("code", code, "message", message));
        return response;
    }
}

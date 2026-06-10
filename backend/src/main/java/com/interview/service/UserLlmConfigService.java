package com.interview.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.interview.dto.llm.LlmConfigRequest;
import com.interview.dto.llm.LlmConfigResponse;
import com.interview.dto.llm.LlmConfigStatusResponse;
import com.interview.dto.llm.LlmConnectionTestRequest;
import com.interview.dto.llm.LlmConnectionTestResponse;
import com.interview.dto.llm.LlmProviderPresetResponse;
import com.interview.entity.UserLlmConfig;
import com.interview.exception.LlmProviderRequiredException;
import com.interview.mapper.UserLlmConfigMapper;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

@Slf4j
@Service
public class UserLlmConfigService {

    private static final double DEFAULT_TEMPERATURE = 0.7;
    private static final int MAX_ERROR_MESSAGE_LENGTH = 200;

    private final UserLlmConfigMapper mapper;
    private final ApiKeyCryptoService cryptoService;
    private final UserLlmModelFactory modelFactory;

    public UserLlmConfigService(UserLlmConfigMapper mapper,
                                ApiKeyCryptoService cryptoService,
                                UserLlmModelFactory modelFactory) {
        this.mapper = mapper;
        this.cryptoService = cryptoService;
        this.modelFactory = modelFactory;
    }

    public List<LlmProviderPresetResponse> presets() {
        return List.of(
                new LlmProviderPresetResponse("deepseek", "DeepSeek", "https://api.deepseek.com/v1", "deepseek-chat", 0.7),
                new LlmProviderPresetResponse("kimi", "Kimi / Moonshot", "https://api.moonshot.cn/v1", "moonshot-v1-8k", 0.7),
                new LlmProviderPresetResponse("glm", "GLM / Zhipu", "https://open.bigmodel.cn/api/paas/v4", "glm-4-flash", 0.7),
                new LlmProviderPresetResponse("qwen", "Qwen", "https://dashscope.aliyuncs.com/compatible-mode/v1", "qwen-plus", 0.7),
                new LlmProviderPresetResponse("custom", "自定义 OpenAI-compatible", "", "", 0.7)
        );
    }

    public List<LlmConfigResponse> list(Long userId) {
        requireUser(userId);
        return mapper.selectList(new LambdaQueryWrapper<UserLlmConfig>()
                        .eq(UserLlmConfig::getUserId, userId)
                        .orderByDesc(UserLlmConfig::getActive)
                        .orderByDesc(UserLlmConfig::getUpdateTime))
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public LlmConfigResponse create(Long userId, LlmConfigRequest request) {
        requireUser(userId);
        validateRequired(request.getProvider(), "Provider 不能为空");
        validateRequired(request.getDisplayName(), "配置名称不能为空");
        validateRequired(request.getBaseUrl(), "Base URL 不能为空");
        validateRequired(request.getModelName(), "模型名称不能为空");
        validateRequired(request.getApiKey(), "API Key 不能为空");
        validateBaseUrl(request.getBaseUrl());

        LocalDateTime now = LocalDateTime.now();
        UserLlmConfig config = new UserLlmConfig();
        config.setUserId(userId);
        config.setProvider(normalizeProvider(request.getProvider()));
        config.setDisplayName(request.getDisplayName().trim());
        config.setBaseUrl(request.getBaseUrl().trim());
        config.setModelName(request.getModelName().trim());
        config.setEncryptedApiKey(cryptoService.encrypt(request.getApiKey().trim()));
        config.setApiKeyHint(maskApiKey(request.getApiKey().trim()));
        config.setTemperature(normalizeTemperature(request.getTemperature()));
        config.setActive(Boolean.TRUE.equals(request.getActive()));
        config.setCreateTime(now);
        config.setUpdateTime(now);

        if (Boolean.TRUE.equals(config.getActive())) {
            deactivateAll(userId);
        }
        mapper.insert(config);
        return toResponse(config);
    }

    @Transactional
    public LlmConfigResponse update(Long userId, Long id, LlmConfigRequest request) {
        UserLlmConfig config = loadOwned(userId, id);
        if (notBlank(request.getProvider())) {
            config.setProvider(normalizeProvider(request.getProvider()));
        }
        if (notBlank(request.getDisplayName())) {
            config.setDisplayName(request.getDisplayName().trim());
        }
        if (notBlank(request.getBaseUrl())) {
            validateBaseUrl(request.getBaseUrl());
            config.setBaseUrl(request.getBaseUrl().trim());
        }
        if (notBlank(request.getModelName())) {
            config.setModelName(request.getModelName().trim());
        }
        if (notBlank(request.getApiKey())) {
            String apiKey = request.getApiKey().trim();
            config.setEncryptedApiKey(cryptoService.encrypt(apiKey));
            config.setApiKeyHint(maskApiKey(apiKey));
        }
        if (request.getTemperature() != null) {
            config.setTemperature(normalizeTemperature(request.getTemperature()));
        }
        if (request.getActive() != null) {
            config.setActive(request.getActive());
            if (Boolean.TRUE.equals(request.getActive())) {
                deactivateAll(userId);
            }
        }
        config.setUpdateTime(LocalDateTime.now());
        mapper.updateById(config);
        return toResponse(config);
    }

    @Transactional
    public void delete(Long userId, Long id) {
        UserLlmConfig config = loadOwned(userId, id);
        mapper.deleteById(config.getId());
    }

    @Transactional
    public LlmConfigResponse activate(Long userId, Long id) {
        UserLlmConfig config = loadOwned(userId, id);
        deactivateAll(userId);
        config.setActive(true);
        config.setUpdateTime(LocalDateTime.now());
        mapper.updateById(config);
        return toResponse(config);
    }

    public LlmConfigStatusResponse status(Long userId) {
        requireUser(userId);
        UserLlmConfig active = findActive(userId);
        LlmConfigStatusResponse response = new LlmConfigStatusResponse();
        response.setConfigured(active != null);
        if (active != null) {
            response.setActiveConfigId(active.getId());
            response.setProvider(active.getProvider());
            response.setDisplayName(active.getDisplayName());
            response.setModelName(active.getModelName());
        }
        return response;
    }

    @Transactional
    public LlmConnectionTestResponse test(Long userId, LlmConnectionTestRequest request) {
        requireUser(userId);
        UserLlmRuntimeConfig runtimeConfig = resolveTestRuntimeConfig(userId, request);
        long started = System.currentTimeMillis();
        LlmConnectionTestResponse response = new LlmConnectionTestResponse();
        try {
            modelFactory.createChatModel(runtimeConfig).generate(List.of(
                    new SystemMessage("You are a connection health checker. Reply with OK only."),
                    new UserMessage("OK")
            ));
            response.setSuccess(true);
            response.setMessage("连接测试成功");
            response.setLatencyMs(System.currentTimeMillis() - started);
            updateTestStatus(runtimeConfig.configId(), userId, "SUCCESS", response.getMessage());
            return response;
        } catch (RuntimeException e) {
            String sanitized = sanitizeError(e.getMessage(), runtimeConfig.apiKey());
            response.setSuccess(false);
            response.setMessage(sanitized);
            response.setLatencyMs(System.currentTimeMillis() - started);
            updateTestStatus(runtimeConfig.configId(), userId, "FAILED", sanitized);
            return response;
        }
    }

    public void ensureActiveProvider(Long userId) {
        requireActiveRuntimeConfig(userId);
    }

    public UserLlmRuntimeConfig requireActiveRuntimeConfig(Long userId) {
        requireUser(userId);
        UserLlmConfig config = findActive(userId);
        if (config == null) {
            throw new LlmProviderRequiredException();
        }
        return toRuntimeConfig(config);
    }

    private UserLlmRuntimeConfig resolveTestRuntimeConfig(Long userId, LlmConnectionTestRequest request) {
        if (request != null && request.getConfigId() != null) {
            return toRuntimeConfig(loadOwned(userId, request.getConfigId()));
        }
        if (request == null) {
            throw new IllegalArgumentException("测试配置不能为空");
        }
        validateRequired(request.getProvider(), "Provider 不能为空");
        validateRequired(request.getBaseUrl(), "Base URL 不能为空");
        validateRequired(request.getModelName(), "模型名称不能为空");
        validateRequired(request.getApiKey(), "API Key 不能为空");
        validateBaseUrl(request.getBaseUrl());
        return new UserLlmRuntimeConfig(
                null,
                userId,
                normalizeProvider(request.getProvider()),
                null,
                request.getBaseUrl().trim(),
                request.getModelName().trim(),
                request.getApiKey().trim(),
                normalizeTemperature(request.getTemperature())
        );
    }

    private UserLlmRuntimeConfig toRuntimeConfig(UserLlmConfig config) {
        return new UserLlmRuntimeConfig(
                config.getId(),
                config.getUserId(),
                config.getProvider(),
                config.getDisplayName(),
                config.getBaseUrl(),
                config.getModelName(),
                cryptoService.decrypt(config.getEncryptedApiKey()),
                normalizeTemperature(config.getTemperature())
        );
    }

    private UserLlmConfig findActive(Long userId) {
        return mapper.selectOne(new LambdaQueryWrapper<UserLlmConfig>()
                .eq(UserLlmConfig::getUserId, userId)
                .eq(UserLlmConfig::getActive, true)
                .last("LIMIT 1"));
    }

    private UserLlmConfig loadOwned(Long userId, Long id) {
        requireUser(userId);
        if (id == null) {
            throw new IllegalArgumentException("配置 ID 不能为空");
        }
        UserLlmConfig config = mapper.selectById(id);
        if (config == null || !userId.equals(config.getUserId())) {
            throw new RuntimeException("大模型配置不存在或无权访问");
        }
        return config;
    }

    private void deactivateAll(Long userId) {
        UserLlmConfig update = new UserLlmConfig();
        update.setActive(false);
        update.setUpdateTime(LocalDateTime.now());
        mapper.update(update, new LambdaUpdateWrapper<UserLlmConfig>()
                .eq(UserLlmConfig::getUserId, userId)
                .eq(UserLlmConfig::getActive, true));
    }

    private void updateTestStatus(Long configId, Long userId, String status, String message) {
        if (configId == null) {
            return;
        }
        UserLlmConfig update = new UserLlmConfig();
        update.setId(configId);
        update.setLastTestStatus(status);
        update.setLastTestMessage(truncate(message, MAX_ERROR_MESSAGE_LENGTH));
        update.setLastTestTime(LocalDateTime.now());
        update.setUpdateTime(LocalDateTime.now());
        mapper.update(update, new LambdaUpdateWrapper<UserLlmConfig>()
                .eq(UserLlmConfig::getId, configId)
                .eq(UserLlmConfig::getUserId, userId));
    }

    private LlmConfigResponse toResponse(UserLlmConfig config) {
        LlmConfigResponse response = new LlmConfigResponse();
        response.setId(config.getId());
        response.setProvider(config.getProvider());
        response.setDisplayName(config.getDisplayName());
        response.setBaseUrl(config.getBaseUrl());
        response.setModelName(config.getModelName());
        response.setApiKeyHint(config.getApiKeyHint());
        response.setTemperature(config.getTemperature());
        response.setActive(Boolean.TRUE.equals(config.getActive()));
        response.setLastTestStatus(config.getLastTestStatus());
        response.setLastTestMessage(config.getLastTestMessage());
        response.setLastTestTime(config.getLastTestTime());
        response.setCreateTime(config.getCreateTime());
        response.setUpdateTime(config.getUpdateTime());
        return response;
    }

    private void validateRequired(String value, String message) {
        if (!notBlank(value)) {
            throw new IllegalArgumentException(message);
        }
    }

    private void validateBaseUrl(String baseUrl) {
        try {
            URI uri = URI.create(baseUrl.trim());
            String scheme = uri.getScheme();
            if (scheme == null || (!scheme.equalsIgnoreCase("https") && !scheme.equalsIgnoreCase("http"))
                    || uri.getHost() == null) {
                throw new IllegalArgumentException("Base URL 必须是 http(s) 地址");
            }
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Base URL 必须是合法的 OpenAI-compatible 地址");
        }
    }

    private String normalizeProvider(String provider) {
        return provider.trim().toLowerCase(Locale.ROOT);
    }

    private double normalizeTemperature(Double temperature) {
        if (temperature == null) {
            return DEFAULT_TEMPERATURE;
        }
        if (temperature < 0 || temperature > 2) {
            throw new IllegalArgumentException("temperature 必须在 0 到 2 之间");
        }
        return temperature;
    }

    private String maskApiKey(String apiKey) {
        if (apiKey.length() <= 6) {
            return "***";
        }
        return apiKey.substring(0, Math.min(2, apiKey.length())) + "..." + apiKey.substring(apiKey.length() - 4);
    }

    private String sanitizeError(String message, String apiKey) {
        String sanitized = message == null || message.isBlank() ? "连接测试失败，请检查配置" : message;
        if (apiKey != null && !apiKey.isBlank()) {
            sanitized = sanitized.replace(apiKey, "[REDACTED]");
        }
        sanitized = sanitized.replaceAll("(?i)Bearer\\s+[A-Za-z0-9._\\-]+", "Bearer [REDACTED]");
        return truncate(sanitized, MAX_ERROR_MESSAGE_LENGTH);
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    private void requireUser(Long userId) {
        if (userId == null) {
            throw new RuntimeException("未登录：缺少用户身份");
        }
    }
}

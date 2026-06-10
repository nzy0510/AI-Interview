package com.interview.controller;

import com.interview.common.Result;
import com.interview.dto.llm.LlmConfigRequest;
import com.interview.dto.llm.LlmConfigResponse;
import com.interview.dto.llm.LlmConfigStatusResponse;
import com.interview.dto.llm.LlmConnectionTestRequest;
import com.interview.dto.llm.LlmConnectionTestResponse;
import com.interview.dto.llm.LlmProviderPresetResponse;
import com.interview.service.UserLlmConfigService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/llm")
public class UserLlmConfigController {

    @Autowired
    private UserLlmConfigService userLlmConfigService;

    @GetMapping("/providers/presets")
    public Result<List<LlmProviderPresetResponse>> presets() {
        return Result.success(userLlmConfigService.presets());
    }

    @GetMapping("/configs")
    public Result<List<LlmConfigResponse>> list(HttpServletRequest request) {
        return Result.success(userLlmConfigService.list(currentUserId(request)));
    }

    @PostMapping("/configs")
    public Result<LlmConfigResponse> create(@RequestBody LlmConfigRequest body,
                                            HttpServletRequest request) {
        return Result.success(userLlmConfigService.create(currentUserId(request), body));
    }

    @PutMapping("/configs/{id}")
    public Result<LlmConfigResponse> update(@PathVariable Long id,
                                            @RequestBody LlmConfigRequest body,
                                            HttpServletRequest request) {
        return Result.success(userLlmConfigService.update(currentUserId(request), id, body));
    }

    @DeleteMapping("/configs/{id}")
    public Result<Void> delete(@PathVariable Long id, HttpServletRequest request) {
        userLlmConfigService.delete(currentUserId(request), id);
        return Result.success();
    }

    @PostMapping("/configs/{id}/activate")
    public Result<LlmConfigResponse> activate(@PathVariable Long id, HttpServletRequest request) {
        return Result.success(userLlmConfigService.activate(currentUserId(request), id));
    }

    @PostMapping("/configs/test")
    public Result<LlmConnectionTestResponse> test(@RequestBody LlmConnectionTestRequest body,
                                                  HttpServletRequest request) {
        return Result.success(userLlmConfigService.test(currentUserId(request), body));
    }

    @GetMapping("/configs/status")
    public Result<LlmConfigStatusResponse> status(HttpServletRequest request) {
        return Result.success(userLlmConfigService.status(currentUserId(request)));
    }

    private Long currentUserId(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("currentUserId");
        if (userId == null) {
            throw new RuntimeException("未登录：缺少用户身份");
        }
        return userId;
    }
}

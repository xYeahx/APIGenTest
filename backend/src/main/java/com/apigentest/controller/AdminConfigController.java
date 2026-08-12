package com.apigentest.controller;

import com.apigentest.common.Result;
import com.apigentest.dto.ConfigValueDTO;
import com.apigentest.service.AdminConfigService;
import com.apigentest.vo.SysConfigVO;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/configs")
public class AdminConfigController {

    private final AdminConfigService adminConfigService;

    public AdminConfigController(AdminConfigService adminConfigService) {
        this.adminConfigService = adminConfigService;
    }

    @GetMapping
    public Result<List<SysConfigVO>> list() {
        return Result.ok(adminConfigService.list());
    }

    @PutMapping("/{key}")
    public Result<Void> update(@PathVariable String key, @Valid @RequestBody ConfigValueDTO dto) {
        adminConfigService.update(key, dto.getValue());
        return Result.ok();
    }

    /** 测试 Webhook 连通性（仅管理员） */
    @PostMapping("/test-webhook")
    public Result<Map<String, String>> testWebhook() {
        return Result.ok(Map.of("message", adminConfigService.testWebhook()));
    }

    /** 用当前已保存的 LLM 配置测试连通性（仅管理员） */
    @PostMapping("/test-llm")
    public Result<Map<String, String>> testLlm() {
        return Result.ok(adminConfigService.testLlm());
    }
}
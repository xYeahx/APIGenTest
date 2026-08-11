package com.apigentest.controller;

import com.apigentest.common.Result;
import com.apigentest.dto.RunRequestDTO;
import com.apigentest.service.CiService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class CiController {

    private final CiService ciService;

    public CiController(CiService ciService) {
        this.ciService = ciService;
    }

    /** CI 触发执行：凭 X-CI-Token 头，免登录，triggerType=3 */
    @PostMapping("/ci/run")
    public Result<Map<String, Long>> run(@RequestHeader(value = "X-CI-Token", required = false) String token,
                                         @Valid @RequestBody RunRequestDTO dto) {
        Long executionId = ciService.runByToken(token, dto);
        return Result.ok(Map.of("executionId", executionId));
    }

    /** CI Token 状态（管理员，脱敏） */
    @GetMapping("/admin/ci/token")
    public Result<Map<String, Object>> tokenInfo() {
        return Result.ok(ciService.tokenInfo());
    }

    /** 重新生成 CI Token（管理员），返回完整 Token（仅本次可见） */
    @PostMapping("/admin/ci/token/regenerate")
    public Result<Map<String, String>> regenerate() {
        return Result.ok(Map.of("token", ciService.regenerateToken()));
    }
}
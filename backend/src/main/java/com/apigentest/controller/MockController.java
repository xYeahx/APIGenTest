package com.apigentest.controller;

import com.apigentest.service.MockService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 内置 Mock 服务（无需登录）：环境 Base URL 填 http://host:port/mock/{projectId} 即可使用。
 * 例：Base URL = http://localhost:8081/mock/1，接口路径 /api/users/1 ->
 * 完整请求 http://localhost:8081/mock/1/api/users/1
 */
@RestController
@RequestMapping("/mock")
public class MockController {

    private final MockService mockService;

    public MockController(MockService mockService) {
        this.mockService = mockService;
    }

    @GetMapping("/{projectId}/**")
    public ResponseEntity<String> get(@PathVariable Long projectId, HttpServletRequest request) {
        return mockService.handle(projectId, "GET", resolvePath(request), request.getParameterMap());
    }

    @PostMapping("/{projectId}/**")
    public ResponseEntity<String> post(@PathVariable Long projectId, HttpServletRequest request) {
        return mockService.handle(projectId, "POST", resolvePath(request), request.getParameterMap());
    }

    @PutMapping("/{projectId}/**")
    public ResponseEntity<String> put(@PathVariable Long projectId, HttpServletRequest request) {
        return mockService.handle(projectId, "PUT", resolvePath(request), request.getParameterMap());
    }

    @DeleteMapping("/{projectId}/**")
    public ResponseEntity<String> delete(@PathVariable Long projectId, HttpServletRequest request) {
        return mockService.handle(projectId, "DELETE", resolvePath(request), request.getParameterMap());
    }

    @PatchMapping("/{projectId}/**")
    public ResponseEntity<String> patch(@PathVariable Long projectId, HttpServletRequest request) {
        return mockService.handle(projectId, "PATCH", resolvePath(request), request.getParameterMap());
    }

    /** 从 /mock/{projectId}/xxx 中提取 /xxx */
    private String resolvePath(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String ctx = request.getContextPath();
        if (ctx != null && !ctx.isBlank() && uri.startsWith(ctx)) {
            uri = uri.substring(ctx.length());
        }
        int first = uri.indexOf('/', 1);
        if (first < 0) {
            return "/";
        }
        int second = uri.indexOf('/', first + 1);
        if (second < 0) {
            return "/";
        }
        return uri.substring(second);
    }
}
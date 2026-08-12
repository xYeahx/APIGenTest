package com.apigentest.service;

import org.springframework.http.ResponseEntity;

import java.util.Map;

/**
 * 内置 Mock 服务：基于项目导入的 OpenAPI schema 自动生成 mock 数据，
 * 无需真实被测系统即可演示「导入 -> 生成 -> 执行 -> 报告」闭环。
 * 支持 mock_error / mock_delay / mock_data / mock_empty 等参数控制返回。
 */
public interface MockService {

    /**
     * 处理一个 mock 请求
     *
     * @param projectId 项目 ID（路径首段）
     * @param method    HTTP 方法
     * @param path      相对路径（如 /api/users/1）
     * @param params    查询参数
     */
    ResponseEntity<String> handle(Long projectId, String method, String path, Map<String, String[]> params);
}
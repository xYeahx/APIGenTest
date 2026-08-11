package com.apigentest.controller;

import com.apigentest.common.Result;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 健康检查：验证服务可启动、数据库可连通
 */
@RestController
@RequestMapping("/api")
public class HealthController {

    private final JdbcTemplate jdbcTemplate;

    public HealthController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping("/health")
    public Result<Map<String, Object>> health() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("status", "ok");
        data.put("time", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        try {
            Integer userCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM `user`", Integer.class);
            data.put("db", true);
            data.put("userCount", userCount);
        } catch (Exception e) {
            data.put("db", false);
            data.put("dbError", e.getMessage());
        }
        return Result.ok(data);
    }
}
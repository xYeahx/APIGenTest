package com.apigentest.service;

import com.apigentest.entity.AuditLog;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

/**
 * 管理操作审计日志
 */
public interface AuditService {

    /** 记录一条审计日志（操作人取当前登录上下文，系统/CI 触发时记为 system） */
    void log(String action, String target, String detail);

    /** 分页查询审计日志（仅管理员以上可用，可按操作类型筛选） */
    Page<AuditLog> list(long page, long size, String action);
}

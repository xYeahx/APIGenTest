package com.apigentest.service.impl;

import com.apigentest.common.BusinessException;
import com.apigentest.common.ErrorCode;
import com.apigentest.common.Roles;
import com.apigentest.common.UserContext;
import com.apigentest.entity.AuditLog;
import com.apigentest.mapper.AuditLogMapper;
import com.apigentest.service.AuditService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 审计日志实现：写入失败不影响主流程（仅记录告警日志）
 */
@Service
public class AuditServiceImpl implements AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditServiceImpl.class);

    private final AuditLogMapper auditLogMapper;

    public AuditServiceImpl(AuditLogMapper auditLogMapper) {
        this.auditLogMapper = auditLogMapper;
    }

    @Override
    public void log(String action, String target, String detail) {
        try {
            AuditLog entry = new AuditLog();
            Long userId = UserContext.getUserId();
            entry.setUserId(userId);
            entry.setUsername(userId == null ? "system" : UserContext.getUsername());
            entry.setAction(action);
            entry.setTarget(target);
            entry.setDetail(detail);
            auditLogMapper.insert(entry);
        } catch (Exception e) {
            log.warn("审计日志写入失败 action={} target={}", action, target, e);
        }
    }

    @Override
    public Page<AuditLog> list(long page, long size, String action) {
        Integer role = UserContext.getRole();
        if (!Roles.isAdmin(role)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "仅管理员或超级管理员可查看审计日志");
        }
        LambdaQueryWrapper<AuditLog> wrapper = new LambdaQueryWrapper<AuditLog>()
                .orderByDesc(AuditLog::getId);
        if (action != null && !action.isBlank()) {
            wrapper.eq(AuditLog::getAction, action.trim());
        }
        return auditLogMapper.selectPage(new Page<>(page, size), wrapper);
    }
}

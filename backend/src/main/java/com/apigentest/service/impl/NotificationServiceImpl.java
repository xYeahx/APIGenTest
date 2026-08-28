package com.apigentest.service.impl;

import com.apigentest.common.BusinessException;
import com.apigentest.common.ErrorCode;
import com.apigentest.common.UserContext;
import com.apigentest.entity.Notification;
import com.apigentest.mapper.ExecutionMapper;
import com.apigentest.mapper.NotificationMapper;
import com.apigentest.service.NotificationService;
import com.apigentest.vo.NotificationVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 站内信通知：执行完成后写入，用户可在右上角铃铛查看
 */
@Service
public class NotificationServiceImpl implements NotificationService {

    private final NotificationMapper notificationMapper;
    private final ExecutionMapper executionMapper;

    public NotificationServiceImpl(NotificationMapper notificationMapper, ExecutionMapper executionMapper) {
        this.notificationMapper = notificationMapper;
        this.executionMapper = executionMapper;
    }

    @Override
    public void notify(Long userId, String type, String title, String content, Long executionId) {
        if (userId == null) {
            return;
        }
        Notification n = new Notification();
        n.setUserId(userId);
        n.setType(type);
        n.setTitle(title);
        n.setContent(content);
        n.setExecutionId(executionId);
        n.setIsRead(0);
        notificationMapper.insert(n);
    }

    @Override
    public Page<NotificationVO> listMine(long page, long size) {
        Page<Notification> nPage = notificationMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<Notification>()
                        .eq(Notification::getUserId, UserContext.getUserId())
                        .orderByDesc(Notification::getId));
        List<Long> execIds = nPage.getRecords().stream()
                .map(Notification::getExecutionId).filter(Objects::nonNull).distinct().toList();
        Map<Long, Long> execProject = new HashMap<>();
        if (!execIds.isEmpty()) {
            executionMapper.selectBatchIds(execIds)
                    .forEach(e -> execProject.put(e.getId(), e.getProjectId()));
        }
        Page<NotificationVO> voPage = new Page<>(nPage.getCurrent(), nPage.getSize(), nPage.getTotal());
        voPage.setRecords(nPage.getRecords().stream().map(n -> toVO(n, execProject)).toList());
        return voPage;
    }

    @Override
    public long unreadCount() {
        return notificationMapper.selectCount(new LambdaQueryWrapper<Notification>()
                .eq(Notification::getUserId, UserContext.getUserId())
                .eq(Notification::getIsRead, 0));
    }

    @Override
    public void markRead(Long id) {
        Notification n = notificationMapper.selectById(id);
        if (n == null || !n.getUserId().equals(UserContext.getUserId())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "通知不存在");
        }
        if (n.getIsRead() == null || n.getIsRead() == 0) {
            n.setIsRead(1);
            notificationMapper.updateById(n);
        }
    }

    @Override
    public void markAllRead() {
        notificationMapper.update(null, new LambdaUpdateWrapper<Notification>()
                .eq(Notification::getUserId, UserContext.getUserId())
                .eq(Notification::getIsRead, 0)
                .set(Notification::getIsRead, 1));
    }

    private NotificationVO toVO(Notification n, Map<Long, Long> execProject) {
        NotificationVO vo = new NotificationVO();
        vo.setId(n.getId());
        vo.setUserId(n.getUserId());
        vo.setType(n.getType());
        vo.setTitle(n.getTitle());
        vo.setContent(n.getContent());
        vo.setExecutionId(n.getExecutionId());
        vo.setProjectId(n.getExecutionId() == null ? null : execProject.get(n.getExecutionId()));
        vo.setIsRead(n.getIsRead());
        vo.setCreatedAt(n.getCreatedAt());
        return vo;
    }
}
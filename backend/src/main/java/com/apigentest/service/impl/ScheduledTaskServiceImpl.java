package com.apigentest.service.impl;

import com.apigentest.common.BusinessException;
import com.apigentest.common.UserContext;
import com.apigentest.dto.RunRequestDTO;
import com.apigentest.dto.ScopeDTO;
import com.apigentest.dto.ScheduledTaskDTO;
import com.apigentest.entity.Environment;
import com.apigentest.entity.Execution;
import com.apigentest.entity.Project;
import com.apigentest.entity.ScheduledTask;
import com.apigentest.entity.User;
import com.apigentest.mapper.EnvironmentMapper;
import com.apigentest.mapper.ExecutionMapper;
import com.apigentest.mapper.ProjectMapper;
import com.apigentest.mapper.ScheduledTaskMapper;
import com.apigentest.mapper.UserMapper;
import com.apigentest.service.ExecutionService;
import com.apigentest.service.NotificationService;
import com.apigentest.service.ProjectService;
import com.apigentest.service.ScheduledTaskService;
import com.apigentest.vo.ScheduledTaskVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 定时任务：CRUD + 手动触发 + cron 调度扫描
 * 扫描策略：内存维护每个任务的下次触发时间（nextRunTimes），每 10s 轮询一次，
 * 到期则执行；用 runningIds 防止同一任务重复触发。服务重启后按当前时间重新计算下次触发（错过不补跑）。
 */
@Service
public class ScheduledTaskServiceImpl implements ScheduledTaskService {

    private static final Logger log = LoggerFactory.getLogger(ScheduledTaskServiceImpl.class);
    private final Set<Long> runningIds = ConcurrentHashMap.newKeySet();
    private final Map<Long, LocalDateTime> nextRunTimes = new ConcurrentHashMap<>();

    private final ScheduledTaskMapper taskMapper;
    private final EnvironmentMapper environmentMapper;
    private final ProjectMapper projectMapper;
    private final UserMapper userMapper;
    private final ExecutionMapper executionMapper;
    private final ProjectService projectService;
    private final ExecutionService executionService;
    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;

    public ScheduledTaskServiceImpl(ScheduledTaskMapper taskMapper, EnvironmentMapper environmentMapper,
                                    ProjectMapper projectMapper, UserMapper userMapper,
                                    ExecutionMapper executionMapper, ProjectService projectService,
                                    ExecutionService executionService, NotificationService notificationService,
                                    ObjectMapper objectMapper) {
        this.taskMapper = taskMapper;
        this.environmentMapper = environmentMapper;
        this.projectMapper = projectMapper;
        this.userMapper = userMapper;
        this.executionMapper = executionMapper;
        this.projectService = projectService;
        this.executionService = executionService;
        this.notificationService = notificationService;
        this.objectMapper = objectMapper;
    }

    @Override
    public Page<ScheduledTaskVO> list(Long projectId, long page, long size) {
        projectService.getOwnedProject(projectId);
        Page<ScheduledTask> taskPage = taskMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<ScheduledTask>()
                        .eq(ScheduledTask::getProjectId, projectId)
                        .orderByDesc(ScheduledTask::getId));
        List<ScheduledTask> records = taskPage.getRecords();
        Map<Long, String> envNames = envNamesOf(records);
        Map<Long, String> userNames = userNamesOf(records);
        Page<ScheduledTaskVO> voPage = new Page<>(taskPage.getCurrent(), taskPage.getSize(), taskPage.getTotal());
        voPage.setRecords(records.stream().map(t -> toVO(t, envNames, userNames)).toList());
        return voPage;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ScheduledTaskVO create(Long projectId, ScheduledTaskDTO dto) {
        projectService.getOwnedProject(projectId);
        String cron = normalizeAndValidateCron(dto.getCron());
        requireEnv(projectId, dto.getEnvironmentId());
        ScheduledTask task = new ScheduledTask();
        task.setProjectId(projectId);
        task.setName(dto.getName().trim());
        task.setCron(cron);
        task.setEnvironmentId(dto.getEnvironmentId());
        task.setCaseFilter(serializeScope(dto.getScope()));
        task.setEnabled(dto.getEnabled() == null ? 1 : (dto.getEnabled() == 1 ? 1 : 0));
        task.setCreatorId(UserContext.getUserId());
        taskMapper.insert(task);
        task = taskMapper.selectById(task.getId());
        nextRunTimes.put(task.getId(), CronExpression.parse(cron).next(LocalDateTime.now()));
        return toVO(task, Map.of(task.getEnvironmentId(), environmentMapper.selectById(task.getEnvironmentId()).getName()),
                Map.of(UserContext.getUserId(), UserContext.getUsername()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ScheduledTaskVO update(Long id, ScheduledTaskDTO dto) {
        ScheduledTask task = requireOwnedTask(id);
        String cron = normalizeAndValidateCron(dto.getCron());
        requireEnv(task.getProjectId(), dto.getEnvironmentId());
        task.setName(dto.getName().trim());
        task.setCron(cron);
        task.setEnvironmentId(dto.getEnvironmentId());
        task.setCaseFilter(serializeScope(dto.getScope()));
        if (dto.getEnabled() != null) {
            task.setEnabled(dto.getEnabled() == 1 ? 1 : 0);
        }
        taskMapper.updateById(task);
        nextRunTimes.put(id, CronExpression.parse(cron).next(LocalDateTime.now()));
        return toVO(task, Map.of(task.getEnvironmentId(), environmentMapper.selectById(task.getEnvironmentId()).getName()),
                Map.of(task.getCreatorId(), userMapper.selectById(task.getCreatorId()).getUsername()));
    }

    @Override
    public void delete(Long id) {
        requireOwnedTask(id);
        taskMapper.deleteById(id);
        nextRunTimes.remove(id);
    }

    @Override
    public void updateStatus(Long id, Integer enabled) {
        ScheduledTask task = requireOwnedTask(id);
        task.setEnabled(enabled != null && enabled == 1 ? 1 : 0);
        taskMapper.updateById(task);
        if (task.getEnabled() == 1) {
            nextRunTimes.put(id, CronExpression.parse(task.getCron()).next(LocalDateTime.now()));
        } else {
            nextRunTimes.remove(id);
        }
    }

    @Override
    public Long runNow(Long id) {
        ScheduledTask task = requireOwnedTask(id);
        if (task.getEnabled() == null || task.getEnabled() != 1) {
            throw new BusinessException(400, "任务已停用，请先启用");
        }
        if (!runningIds.add(id)) {
            throw new BusinessException(400, "任务正在执行中，请稍后再试");
        }
        try {
            return trigger(task);
        } finally {
            runningIds.remove(id);
            nextRunTimes.put(id, CronExpression.parse(task.getCron()).next(LocalDateTime.now()));
        }
    }

    @Override
    public Map<String, Object> cronPreview(String cron) {
        String normalized = normalizeAndValidateCron(cron);
        Map<String, Object> result = new HashMap<>();
        result.put("cron", normalized);
        result.put("nextRunAt", CronExpression.parse(normalized).next(LocalDateTime.now()));
        return result;
    }

    @Override
    @Scheduled(fixedDelay = 10000)
    public void scanDueTasks() {
        List<ScheduledTask> tasks = taskMapper.selectList(
                new LambdaQueryWrapper<ScheduledTask>().eq(ScheduledTask::getEnabled, 1));
        LocalDateTime now = LocalDateTime.now();
        for (ScheduledTask task : tasks) {
            try {
                CronExpression cron = CronExpression.parse(task.getCron());
                LocalDateTime next = nextRunTimes.computeIfAbsent(task.getId(),
                        k -> CronExpression.parse(task.getCron()).next(now));
                if (next == null || next.isAfter(now)) {
                    continue;
                }
                if (!runningIds.add(task.getId())) {
                    continue;
                }
                try {
                    Long executionId = trigger(task);
                    log.info("定时任务触发成功 taskId={} executionId={}", task.getId(), executionId);
                } catch (Exception e) {
                    log.error("定时任务触发失败 taskId={}", task.getId(), e);
                } finally {
                    runningIds.remove(task.getId());
                    nextRunTimes.put(task.getId(), cron.next(LocalDateTime.now()));
                }
            } catch (Exception e) {
                log.warn("定时任务扫描异常 taskId={}", task.getId(), e);
            }
        }
    }

    // ---------- 私有方法 ----------

    private Long trigger(ScheduledTask task) {
        ScopeDTO scope = parseScope(task.getCaseFilter());
        RunRequestDTO dto = new RunRequestDTO();
        dto.setProjectId(task.getProjectId());
        dto.setEnvironmentId(task.getEnvironmentId());
        dto.setScope(scope);
        // 先记录触发时间，避免失败后反复重试
        task.setLastRunAt(LocalDateTime.now());
        taskMapper.updateById(task);
        return executionService.runBySystem(dto, 2, task.getCreatorId(),
                executionId -> notifyFinished(task, executionId));
    }

    private void notifyFinished(ScheduledTask task, Long executionId) {
        try {
            Execution e = executionMapper.selectById(executionId);
            if (e == null) {
                return;
            }
            Long userId = task.getCreatorId();
            if (userId == null) {
                Project p = projectMapper.selectById(task.getProjectId());
                userId = p == null ? null : p.getOwnerId();
            }
            if (userId == null) {
                return;
            }
            int total = e.getTotalCases() == null ? 0 : e.getTotalCases();
            int passed = e.getPassed() == null ? 0 : e.getPassed();
            int failed = e.getFailed() == null ? 0 : e.getFailed();
            double rate = total == 0 ? 0.0 : Math.round(passed * 1000.0 / total) / 10.0;
            String title = "定时任务执行完成：" + task.getName();
            String content = String.format("共 %d 条用例，通过 %d，失败 %d，通过率 %.1f%%", total, passed, failed, rate);
            notificationService.notify(userId, "execution", title, content, executionId);
        } catch (Exception ex) {
            log.error("定时任务通知发送失败 taskId={}", task.getId(), ex);
        }
    }

    private ScheduledTask requireOwnedTask(Long id) {
        ScheduledTask task = taskMapper.selectById(id);
        if (task == null) {
            throw new BusinessException(404, "定时任务不存在");
        }
        projectService.getOwnedProject(task.getProjectId());
        return task;
    }

    private Environment requireEnv(Long projectId, Long envId) {
        Environment env = environmentMapper.selectById(envId);
        if (env == null || !env.getProjectId().equals(projectId)) {
            throw new BusinessException(400, "执行环境不存在或不属于该项目");
        }
        return env;
    }

    private String normalizeAndValidateCron(String cron) {
        if (cron == null || cron.isBlank()) {
            throw new BusinessException(400, "cron 表达式不能为空");
        }
        String t = cron.trim();
        int parts = t.split("\\s+").length;
        if (parts == 5) {
            t = "0 " + t;
        }
        try {
            CronExpression.parse(t);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(400, "cron 表达式无效：" + e.getMessage());
        }
        return t;
    }

    private String serializeScope(ScopeDTO scope) {
        if (scope == null || scope.getType() == null || scope.getType().isBlank()) {
            throw new BusinessException(400, "执行范围不能为空");
        }
        String type = scope.getType().trim();
        if ("all".equals(type)) {
            return "{\"type\":\"all\"}";
        }
        if ("caseIds".equals(type)) {
            if (scope.getCaseIds() == null || scope.getCaseIds().isEmpty()) {
                throw new BusinessException(400, "请选择要执行的用例");
            }
            try {
                return objectMapper.writeValueAsString(Map.of("type", "caseIds", "caseIds", scope.getCaseIds()));
            } catch (JsonProcessingException e) {
                throw new BusinessException(400, "执行范围序列化失败");
            }
        }
        throw new BusinessException(400, "不支持的执行范围类型：" + type);
    }

    private ScopeDTO parseScope(String filter) {
        ScopeDTO scope = new ScopeDTO();
        if (filter == null || filter.isBlank()) {
            scope.setType("all");
            return scope;
        }
        try {
            JsonNode node = objectMapper.readTree(filter);
            scope.setType(node.path("type").asText("all"));
            if (node.has("caseIds") && node.get("caseIds").isArray()) {
                List<Long> ids = new ArrayList<>();
                node.get("caseIds").forEach(n -> ids.add(n.asLong()));
                scope.setCaseIds(ids);
            }
            return scope;
        } catch (Exception e) {
            throw new BusinessException(500, "任务执行范围解析失败");
        }
    }

    private Map<Long, String> envNamesOf(List<ScheduledTask> tasks) {
        List<Long> ids = tasks.stream().map(ScheduledTask::getEnvironmentId).distinct().toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return environmentMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(Environment::getId, Environment::getName, (a, b) -> a));
    }

    private Map<Long, String> userNamesOf(List<ScheduledTask> tasks) {
        List<Long> ids = tasks.stream().map(ScheduledTask::getCreatorId)
                .filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return userMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(User::getId, User::getUsername, (a, b) -> a));
    }

    private ScheduledTaskVO toVO(ScheduledTask t, Map<Long, String> envNames, Map<Long, String> userNames) {
        ScheduledTaskVO vo = new ScheduledTaskVO();
        vo.setId(t.getId());
        vo.setProjectId(t.getProjectId());
        vo.setName(t.getName());
        vo.setCron(t.getCron());
        vo.setEnvironmentId(t.getEnvironmentId());
        vo.setEnvName(envNames.get(t.getEnvironmentId()));
        vo.setScope(t.getCaseFilter());
        vo.setEnabled(t.getEnabled());
        vo.setCreatorName(userNames.get(t.getCreatorId()));
        vo.setLastRunAt(t.getLastRunAt());
        vo.setNextRunAt(nextRunAt(t));
        vo.setCreatedAt(t.getCreatedAt());
        return vo;
    }

    private LocalDateTime nextRunAt(ScheduledTask t) {
        try {
            return CronExpression.parse(t.getCron()).next(LocalDateTime.now());
        } catch (Exception e) {
            return null;
        }
    }
}
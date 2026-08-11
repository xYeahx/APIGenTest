package com.apigentest.service.impl;

import com.apigentest.common.BusinessException;
import com.apigentest.common.UserContext;
import com.apigentest.dto.ProjectDTO;
import com.apigentest.entity.ApiInfo;
import com.apigentest.entity.Environment;
import com.apigentest.entity.Execution;
import com.apigentest.entity.ExecutionDetail;
import com.apigentest.entity.FailureAnalysis;
import com.apigentest.entity.Notification;
import com.apigentest.entity.Project;
import com.apigentest.entity.ScheduledTask;
import com.apigentest.entity.TestCase;
import com.apigentest.mapper.ApiInfoMapper;
import com.apigentest.mapper.EnvironmentMapper;
import com.apigentest.mapper.ExecutionDetailMapper;
import com.apigentest.mapper.FailureAnalysisMapper;
import com.apigentest.mapper.ExecutionMapper;
import com.apigentest.mapper.NotificationMapper;
import com.apigentest.mapper.ProjectMapper;
import com.apigentest.mapper.ScheduledTaskMapper;
import com.apigentest.mapper.TestCaseMapper;
import com.apigentest.service.ProjectService;
import com.apigentest.vo.ProjectVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ProjectServiceImpl implements ProjectService {

    private final ProjectMapper projectMapper;
    private final ApiInfoMapper apiInfoMapper;
    private final EnvironmentMapper environmentMapper;
    private final TestCaseMapper testCaseMapper;
    private final ExecutionMapper executionMapper;
    private final ExecutionDetailMapper executionDetailMapper;
    private final FailureAnalysisMapper failureAnalysisMapper;
    private final ScheduledTaskMapper scheduledTaskMapper;
    private final NotificationMapper notificationMapper;

    public ProjectServiceImpl(ProjectMapper projectMapper, ApiInfoMapper apiInfoMapper,
                              EnvironmentMapper environmentMapper, TestCaseMapper testCaseMapper,
                              ExecutionMapper executionMapper, ExecutionDetailMapper executionDetailMapper,
                              FailureAnalysisMapper failureAnalysisMapper,
                              ScheduledTaskMapper scheduledTaskMapper, NotificationMapper notificationMapper) {
        this.projectMapper = projectMapper;
        this.apiInfoMapper = apiInfoMapper;
        this.environmentMapper = environmentMapper;
        this.testCaseMapper = testCaseMapper;
        this.executionMapper = executionMapper;
        this.executionDetailMapper = executionDetailMapper;
        this.failureAnalysisMapper = failureAnalysisMapper;
        this.scheduledTaskMapper = scheduledTaskMapper;
        this.notificationMapper = notificationMapper;
    }

    @Override
    public List<ProjectVO> listMyProjects() {
        LambdaQueryWrapper<Project> wrapper = new LambdaQueryWrapper<>();
        if (UserContext.getRole() == null || UserContext.getRole() != 2) {
            wrapper.eq(Project::getOwnerId, UserContext.getUserId());
        }
        wrapper.orderByDesc(Project::getId);
        return projectMapper.selectList(wrapper).stream().map(this::toVO).toList();
    }

    @Override
    public ProjectVO createProject(ProjectDTO dto) {
        Project project = new Project();
        project.setName(dto.getName());
        project.setDescription(dto.getDescription());
        project.setOwnerId(UserContext.getUserId());
        projectMapper.insert(project);
        return toVO(project);
    }

    @Override
    public ProjectVO updateProject(Long id, ProjectDTO dto) {
        Project project = getOwnedProject(id);
        project.setName(dto.getName());
        project.setDescription(dto.getDescription());
        projectMapper.updateById(project);
        return toVO(project);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteProject(Long id) {
        getOwnedProject(id);
        // 级联删除：执行明细 -> 执行记录 -> 用例 -> 接口 -> 环境 -> 项目
        //（failure_analysis 通过 execution_detail 外键级联删除）
        List<Long> executionIds = executionIdsOf(id);
        if (!executionIds.isEmpty()) {
            // ???????failure_analysis ??? NO ACTION???????
            List<Long> detailIds = executionDetailMapper.selectList(
                            new LambdaQueryWrapper<ExecutionDetail>()
                                    .in(ExecutionDetail::getExecutionId, executionIds))
                    .stream().map(ExecutionDetail::getId).toList();
            if (!detailIds.isEmpty()) {
                failureAnalysisMapper.delete(new LambdaQueryWrapper<FailureAnalysis>()
                        .in(FailureAnalysis::getExecutionDetailId, detailIds));
            }
            executionDetailMapper.delete(new LambdaQueryWrapper<ExecutionDetail>()
                    .in(ExecutionDetail::getExecutionId, executionIds));
        }
        if (!executionIds.isEmpty()) {
            notificationMapper.delete(new LambdaQueryWrapper<Notification>()
                    .in(Notification::getExecutionId, executionIds));
        }
        executionMapper.delete(new LambdaQueryWrapper<Execution>().eq(Execution::getProjectId, id));
        scheduledTaskMapper.delete(new LambdaQueryWrapper<ScheduledTask>().eq(ScheduledTask::getProjectId, id));
        // 先解除用例间 pre_case_id 引用，再删除用例（外键 fk_case_pre）
        testCaseMapper.update(null, new LambdaUpdateWrapper<TestCase>()
                .eq(TestCase::getProjectId, id)
                .set(TestCase::getPreCaseId, null));
        testCaseMapper.delete(new LambdaQueryWrapper<TestCase>().eq(TestCase::getProjectId, id));
        apiInfoMapper.delete(new LambdaQueryWrapper<ApiInfo>().eq(ApiInfo::getProjectId, id));
        environmentMapper.delete(new LambdaQueryWrapper<Environment>().eq(Environment::getProjectId, id));
        projectMapper.deleteById(id);
    }

    @Override
    public Project getOwnedProject(Long id) {
        Project project = projectMapper.selectById(id);
        if (project == null) {
            throw new BusinessException(404, "项目不存在");
        }
        Integer role = UserContext.getRole();
        boolean isAdmin = role != null && role == 2;
        if (!isAdmin && !project.getOwnerId().equals(UserContext.getUserId())) {
            throw new BusinessException(403, "无权操作该项目");
        }
        return project;
    }

    /** 查询项目下全部执行ID，供级联删除 execution_detail */
    @Override
    public Project requireProject(Long id) {
        Project project = projectMapper.selectById(id);
        if (project == null) {
            throw new BusinessException(404, "项目不存在");
        }
        return project;
    }

    @Override
    public ProjectVO getProject(Long id) {
        Project project = getOwnedProject(id);
        ProjectVO vo = toVO(project);
        vo.setApiCount(apiInfoMapper.selectCount(
                new LambdaQueryWrapper<ApiInfo>().eq(ApiInfo::getProjectId, id)));
        vo.setCaseCount(testCaseMapper.selectCount(
                new LambdaQueryWrapper<TestCase>().eq(TestCase::getProjectId, id)));
        vo.setEnvCount(environmentMapper.selectCount(
                new LambdaQueryWrapper<Environment>().eq(Environment::getProjectId, id)));
        return vo;
    }

    private List<Long> executionIdsOf(Long projectId) {
        return executionMapper.selectList(
                        new LambdaQueryWrapper<Execution>().eq(Execution::getProjectId, projectId))
                .stream().map(Execution::getId).toList();
    }

    private ProjectVO toVO(Project project) {
        ProjectVO vo = new ProjectVO();
        vo.setId(project.getId());
        vo.setName(project.getName());
        vo.setDescription(project.getDescription());
        vo.setOwnerId(project.getOwnerId());
        vo.setCreatedAt(project.getCreatedAt());
        return vo;
    }
}
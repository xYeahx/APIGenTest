package com.apigentest.service.impl;

import com.apigentest.common.BusinessException;
import com.apigentest.common.ErrorCode;
import com.apigentest.common.UserContext;
import com.apigentest.dto.MemberDTO;
import com.apigentest.dto.ProjectDTO;
import com.apigentest.entity.ApiInfo;
import com.apigentest.entity.Environment;
import com.apigentest.entity.Execution;
import com.apigentest.entity.ExecutionDetail;
import com.apigentest.entity.FailureAnalysis;
import com.apigentest.entity.GenerationRecord;
import com.apigentest.entity.Notification;
import com.apigentest.entity.Project;
import com.apigentest.entity.ProjectMember;
import com.apigentest.entity.ScheduledTask;
import com.apigentest.entity.TestCase;
import com.apigentest.entity.User;
import com.apigentest.mapper.ApiInfoMapper;
import com.apigentest.mapper.EnvironmentMapper;
import com.apigentest.mapper.ExecutionDetailMapper;
import com.apigentest.mapper.FailureAnalysisMapper;
import com.apigentest.mapper.GenerationRecordMapper;
import com.apigentest.mapper.ExecutionMapper;
import com.apigentest.mapper.NotificationMapper;
import com.apigentest.mapper.ProjectMapper;
import com.apigentest.mapper.ProjectMemberMapper;
import com.apigentest.mapper.ScheduledTaskMapper;
import com.apigentest.mapper.TestCaseMapper;
import com.apigentest.mapper.UserMapper;
import com.apigentest.service.ProjectService;
import com.apigentest.vo.MemberVO;
import com.apigentest.vo.ProjectVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ProjectServiceImpl implements ProjectService {

    private final ProjectMapper projectMapper;
    private final ProjectMemberMapper memberMapper;
    private final UserMapper userMapper;
    private final ApiInfoMapper apiInfoMapper;
    private final EnvironmentMapper environmentMapper;
    private final TestCaseMapper testCaseMapper;
    private final ExecutionMapper executionMapper;
    private final ExecutionDetailMapper executionDetailMapper;
    private final FailureAnalysisMapper failureAnalysisMapper;
    private final ScheduledTaskMapper scheduledTaskMapper;
    private final NotificationMapper notificationMapper;
    private final GenerationRecordMapper generationRecordMapper;

    public ProjectServiceImpl(ProjectMapper projectMapper, ProjectMemberMapper memberMapper, UserMapper userMapper,
                              ApiInfoMapper apiInfoMapper, EnvironmentMapper environmentMapper,
                              TestCaseMapper testCaseMapper, ExecutionMapper executionMapper,
                              ExecutionDetailMapper executionDetailMapper, FailureAnalysisMapper failureAnalysisMapper,
                              ScheduledTaskMapper scheduledTaskMapper, NotificationMapper notificationMapper,
                              GenerationRecordMapper generationRecordMapper) {
        this.projectMapper = projectMapper;
        this.memberMapper = memberMapper;
        this.userMapper = userMapper;
        this.apiInfoMapper = apiInfoMapper;
        this.environmentMapper = environmentMapper;
        this.testCaseMapper = testCaseMapper;
        this.executionMapper = executionMapper;
        this.executionDetailMapper = executionDetailMapper;
        this.failureAnalysisMapper = failureAnalysisMapper;
        this.scheduledTaskMapper = scheduledTaskMapper;
        this.notificationMapper = notificationMapper;
        this.generationRecordMapper = generationRecordMapper;
    }

    @Override
    public List<ProjectVO> listMyProjects() {
        LambdaQueryWrapper<Project> wrapper = new LambdaQueryWrapper<>();
        if (UserContext.getRole() == null || UserContext.getRole() < 2) {
            // 我创建的 + 我参与的
            wrapper.and(w -> w.eq(Project::getOwnerId, UserContext.getUserId())
                    .or().inSql(Project::getId,
                            "SELECT project_id FROM project_member WHERE user_id = " + UserContext.getUserId()));
        }
        wrapper.orderByDesc(Project::getId);
        List<Project> projects = projectMapper.selectList(wrapper);
        Map<Long, ProjectMember> memberMap = loadMemberMap(projects, UserContext.getUserId());
        return projects.stream().map(p -> toVO(p, memberMap.get(p.getId()))).toList();
    }

    @Override
    public ProjectVO createProject(ProjectDTO dto) {
        Project project = new Project();
        project.setName(dto.getName());
        project.setDescription(dto.getDescription());
        project.setOwnerId(UserContext.getUserId());
        projectMapper.insert(project);
        return toVO(project, null);
    }

    @Override
    public ProjectVO updateProject(Long id, ProjectDTO dto) {
        Project project = getOwnedProject(id);
        project.setName(dto.getName());
        project.setDescription(dto.getDescription());
        projectMapper.updateById(project);
        return toVO(project, null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteProject(Long id) {
        getOwnedProject(id);
        // 级联删除：执行明细 -> 执行记录 -> 用例 -> 接口 -> 环境 -> 成员 -> 项目
        //（failure_analysis 通过 execution_detail 外键级联删除）
        List<Long> executionIds = executionIdsOf(id);
        if (!executionIds.isEmpty()) {
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
        generationRecordMapper.delete(new LambdaQueryWrapper<GenerationRecord>().eq(GenerationRecord::getProjectId, id));
        apiInfoMapper.delete(new LambdaQueryWrapper<ApiInfo>().eq(ApiInfo::getProjectId, id));
        environmentMapper.delete(new LambdaQueryWrapper<Environment>().eq(Environment::getProjectId, id));
        memberMapper.delete(new LambdaQueryWrapper<ProjectMember>().eq(ProjectMember::getProjectId, id));
        projectMapper.deleteById(id);
    }

    @Override
    public Project getOwnedProject(Long id) {
        return requireAccess(id, LEVEL_MANAGE);
    }

    @Override
    public Project requireRead(Long id) {
        return requireAccess(id, LEVEL_READ);
    }

    @Override
    public Project requireWrite(Long id) {
        return requireAccess(id, LEVEL_WRITE);
    }

    @Override
    public Project requireAccess(Long id, int level) {
        Project project = requireProject(id);
        if (userProjectLevel(UserContext.getUserId(), project) < level) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权访问该项目");
        }
        return project;
    }

    @Override
    public Project requireProject(Long id) {
        Project project = projectMapper.selectById(id);
        if (project == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "项目不存在");
        }
        return project;
    }

    @Override
    public ProjectVO getProject(Long id) {
        Project project = requireRead(id);
        ProjectVO vo = toVO(project, null);
        vo.setApiCount(apiInfoMapper.selectCount(
                new LambdaQueryWrapper<ApiInfo>().eq(ApiInfo::getProjectId, id)));
        vo.setCaseCount(testCaseMapper.selectCount(
                new LambdaQueryWrapper<TestCase>().eq(TestCase::getProjectId, id)));
        vo.setEnvCount(environmentMapper.selectCount(
                new LambdaQueryWrapper<Environment>().eq(Environment::getProjectId, id)));
        vo.setOwnerName(ownerName(project.getOwnerId()));
        vo.setMyRole(myRole(project));
        return vo;
    }

    // ---------- 团队协作 ----------

    @Override
    public List<MemberVO> listMembers(Long projectId) {
        Project project = requireRead(projectId);
        List<ProjectMember> members = memberMapper.selectList(new LambdaQueryWrapper<ProjectMember>()
                .eq(ProjectMember::getProjectId, projectId)
                .orderByAsc(ProjectMember::getId));
        Map<Long, User> userMap = usersOf(members);
        List<MemberVO> list = new ArrayList<>();
        list.add(ownerVO(project));
        for (ProjectMember pm : members) {
            MemberVO vo = new MemberVO();
            vo.setId(pm.getId());
            vo.setProjectId(pm.getProjectId());
            vo.setUserId(pm.getUserId());
            vo.setRole(pm.getRole());
            vo.setCreatedAt(pm.getCreatedAt());
            User u = userMap.get(pm.getUserId());
            if (u != null) {
                vo.setUsername(u.getUsername());
                vo.setNickname(u.getNickname());
                vo.setAvatarUrl(u.getAvatarUrl());
                vo.setEmail(u.getEmail());
                vo.setPhone(u.getPhone());
            }
            list.add(vo);
        }
        return list;
    }

    @Override
    public void addMember(Long projectId, MemberDTO dto) {
        Project project = getOwnedProject(projectId);
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, dto.getUsername().trim()));
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "用户不存在");
        }
        if (user.getStatus() != null && user.getStatus() == 0) {
            throw new BusinessException(ErrorCode.ILLEGAL_STATE, "该用户已被禁用，无法邀请");
        }
        if (user.getRole() != null && user.getRole() >= 2) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "管理员/超级管理员可访问所有项目，无需邀请");
        }
        if (user.getId().equals(project.getOwnerId())) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "项目创建人默认为所有者，无需邀请");
        }
        if (user.getId().equals(UserContext.getUserId())) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "不能邀请自己");
        }
        Integer role = dto.getRole();
        if (role == null || (role != 1 && role != 2)) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "角色仅支持 1=成员 / 2=只读成员");
        }
        Long exist = memberMapper.selectCount(new LambdaQueryWrapper<ProjectMember>()
                .eq(ProjectMember::getProjectId, projectId)
                .eq(ProjectMember::getUserId, user.getId()));
        if (exist != null && exist > 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "该用户已是项目成员");
        }
        ProjectMember pm = new ProjectMember();
        pm.setProjectId(projectId);
        pm.setUserId(user.getId());
        pm.setRole(role);
        memberMapper.insert(pm);
        Notification n = new Notification();
        n.setUserId(user.getId());
        n.setType("member");
        n.setTitle("项目邀请");
        n.setContent("您被邀请加入项目「" + project.getName() + "」，角色：" + (role == 1 ? "成员" : "只读成员"));
        n.setIsRead(0);
        notificationMapper.insert(n);
    }

    @Override
    public void updateMember(Long memberId, MemberDTO dto) {
        ProjectMember pm = requireMember(memberId);
        getOwnedProject(pm.getProjectId());
        Integer role = dto.getRole();
        if (role == null || (role != 1 && role != 2)) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "角色仅支持 1=成员 / 2=只读成员");
        }
        pm.setRole(role);
        memberMapper.updateById(pm);
    }

    @Override
    public void removeMember(Long memberId) {
        ProjectMember pm = requireMember(memberId);
        getOwnedProject(pm.getProjectId());
        memberMapper.deleteById(memberId);
    }

    // ---------- 私有方法 ----------

    private ProjectMember requireMember(Long memberId) {
        ProjectMember pm = memberMapper.selectById(memberId);
        if (pm == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "成员记录不存在");
        }
        return pm;
    }

    /** 当前用户在指定项目中的访问级别（管理员/所有者=M，成员=W，只读=R，其他=N） */
    private int userProjectLevel(Long userId, Project project) {
        if (UserContext.getRole() != null && UserContext.getRole() >= 2) {
            return LEVEL_MANAGE;
        }
        if (project.getOwnerId().equals(userId)) {
            return LEVEL_MANAGE;
        }
        ProjectMember pm = memberMapper.selectOne(new LambdaQueryWrapper<ProjectMember>()
                .eq(ProjectMember::getProjectId, project.getId())
                .eq(ProjectMember::getUserId, userId));
        if (pm == null) {
            return LEVEL_NONE;
        }
        return pm.getRole() != null && pm.getRole() == 2 ? LEVEL_READ : LEVEL_WRITE;
    }

    /** 当前用户在项目详情中展示的角色：0 所有者 / 1 成员 / 2 只读成员（管理员恒为 0） */
    private int myRole(Project project) {
        Integer role = UserContext.getRole();
        if (role != null && role >= 2) {
            return 0;
        }
        if (project.getOwnerId().equals(UserContext.getUserId())) {
            return 0;
        }
        ProjectMember pm = memberMapper.selectOne(new LambdaQueryWrapper<ProjectMember>()
                .eq(ProjectMember::getProjectId, project.getId())
                .eq(ProjectMember::getUserId, UserContext.getUserId()));
        return pm == null ? 2 : pm.getRole();
    }

    private Map<Long, ProjectMember> loadMemberMap(List<Project> projects, Long userId) {
        if (projects.isEmpty()) {
            return Map.of();
        }
        List<Long> ids = projects.stream().map(Project::getId).toList();
        return memberMapper.selectList(new LambdaQueryWrapper<ProjectMember>()
                        .eq(ProjectMember::getUserId, userId)
                        .in(ProjectMember::getProjectId, ids))
                .stream().collect(Collectors.toMap(ProjectMember::getProjectId, Function.identity(), (a, b) -> a));
    }

    private Map<Long, User> usersOf(List<ProjectMember> members) {
        List<Long> ids = members.stream().map(ProjectMember::getUserId).distinct().toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return userMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(User::getId, Function.identity(), (a, b) -> a));
    }

    private MemberVO ownerVO(Project project) {
        MemberVO vo = new MemberVO();
        vo.setProjectId(project.getId());
        vo.setUserId(project.getOwnerId());
        vo.setRole(0);
        vo.setCreatedAt(project.getCreatedAt());
        User u = userMapper.selectById(project.getOwnerId());
        if (u != null) {
            vo.setUsername(u.getUsername());
            vo.setNickname(u.getNickname());
            vo.setAvatarUrl(u.getAvatarUrl());
            vo.setEmail(u.getEmail());
            vo.setPhone(u.getPhone());
        }
        return vo;
    }

    private String ownerName(Long ownerId) {
        User u = userMapper.selectById(ownerId);
        return u == null ? null : u.getUsername();
    }

    private List<Long> executionIdsOf(Long projectId) {
        return executionMapper.selectList(
                        new LambdaQueryWrapper<Execution>().eq(Execution::getProjectId, projectId))
                .stream().map(Execution::getId).toList();
    }

    private ProjectVO toVO(Project project, ProjectMember member) {
        ProjectVO vo = new ProjectVO();
        vo.setId(project.getId());
        vo.setName(project.getName());
        vo.setDescription(project.getDescription());
        vo.setOwnerId(project.getOwnerId());
        vo.setCreatedAt(project.getCreatedAt());
        if (member != null) {
            vo.setMyRole(member.getRole());
        } else if (project.getOwnerId().equals(UserContext.getUserId())
                || (UserContext.getRole() != null && UserContext.getRole() >= 2)) {
            vo.setMyRole(0);
        } else {
            vo.setMyRole(2);
        }
        return vo;
    }
}
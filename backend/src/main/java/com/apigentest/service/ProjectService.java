package com.apigentest.service;

import com.apigentest.dto.MemberDTO;
import com.apigentest.dto.ProjectDTO;
import com.apigentest.entity.Project;
import com.apigentest.vo.MemberVO;
import com.apigentest.vo.ProjectVO;

import java.util.List;

public interface ProjectService {

    /** 项目访问级别：无权限 / 只读 / 可写 / 管理（所有者或管理员） */
    int LEVEL_NONE = 0;
    int LEVEL_READ = 1;
    int LEVEL_WRITE = 2;
    int LEVEL_MANAGE = 3;

    List<ProjectVO> listMyProjects();

    ProjectVO createProject(ProjectDTO dto);

    ProjectVO updateProject(Long id, ProjectDTO dto);

    void deleteProject(Long id);

    /**
     * 校验项目存在且当前用户有管理权限（所有者 / 管理员），返回项目
     */
    Project getOwnedProject(Long id);

    /**
     * 校验项目存在且当前用户有读权限（所有者 / 成员 / 只读成员 / 管理员），返回项目
     */
    Project requireRead(Long id);

    /**
     * 校验项目存在且当前用户有写权限（所有者 / 成员 / 管理员），返回项目
     */
    Project requireWrite(Long id);

    /**
     * 按访问级别校验项目权限
     */
    Project requireAccess(Long id, int level);

    /**
     * 仅校验项目存在（无用户上下文时使用，如定时任务系统级执行）
     */
    Project requireProject(Long id);

    /**
     * 项目详情（含接口数/用例数/环境数统计），需要项目读权限
     */
    ProjectVO getProject(Long id);

    // ---------- 团队协作 ----------

    /**
     * 项目成员列表（含所有者 role=0）
     */
    List<MemberVO> listMembers(Long projectId);

    /**
     * 邀请成员（仅所有者 / 管理员）
     */
    void addMember(Long projectId, MemberDTO dto);

    /**
     * 修改成员角色（仅所有者 / 管理员）
     */
    void updateMember(Long memberId, MemberDTO dto);

    /**
     * 移除成员（仅所有者 / 管理员）
     */
    void removeMember(Long memberId);
}
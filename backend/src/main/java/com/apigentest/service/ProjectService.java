package com.apigentest.service;

import com.apigentest.dto.ProjectDTO;
import com.apigentest.entity.Project;
import com.apigentest.vo.ProjectVO;

import java.util.List;

public interface ProjectService {

    List<ProjectVO> listMyProjects();

    ProjectVO createProject(ProjectDTO dto);

    ProjectVO updateProject(Long id, ProjectDTO dto);

    void deleteProject(Long id);

    /**
     * 校验项目存在且当前用户有权限（本人或管理员），返回项目
     */
    Project getOwnedProject(Long id);

    /**
     * 仅校验项目存在（无用户上下文时使用，如定时任务系统级执行）
     */
    Project requireProject(Long id);

    /**
     * 项目详情（含接口数/用例数/环境数统计），需要项目权限
     */
    ProjectVO getProject(Long id);
}
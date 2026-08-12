package com.apigentest.controller;

import com.apigentest.common.Result;
import com.apigentest.dto.MemberDTO;
import com.apigentest.service.ProjectService;
import com.apigentest.vo.MemberVO;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 项目成员管理（团队协作）：列表任意项目成员可见；邀请/改角色/移除仅所有者或管理员
 */
@RestController
@RequestMapping("/api")
public class MemberController {

    private final ProjectService projectService;

    public MemberController(ProjectService projectService) {
        this.projectService = projectService;
    }

    /** 项目成员列表（含所有者 role=0） */
    @GetMapping("/projects/{projectId}/members")
    public Result<List<MemberVO>> list(@PathVariable Long projectId) {
        return Result.ok(projectService.listMembers(projectId));
    }

    /** 邀请成员（仅所有者 / 管理员） */
    @PostMapping("/projects/{projectId}/members")
    public Result<Void> add(@PathVariable Long projectId, @Valid @RequestBody MemberDTO dto) {
        projectService.addMember(projectId, dto);
        return Result.ok();
    }

    /** 修改成员角色（仅所有者 / 管理员） */
    @PutMapping("/members/{id}")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody MemberDTO dto) {
        projectService.updateMember(id, dto);
        return Result.ok();
    }

    /** 移除成员（仅所有者 / 管理员） */
    @DeleteMapping("/members/{id}")
    public Result<Void> remove(@PathVariable Long id) {
        projectService.removeMember(id);
        return Result.ok();
    }
}
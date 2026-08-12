package com.apigentest.controller;

import com.apigentest.common.Result;
import com.apigentest.dto.ResetPasswordDTO;
import com.apigentest.dto.RoleDTO;
import com.apigentest.dto.UserStatusDTO;
import com.apigentest.service.AdminUserService;
import com.apigentest.vo.UserAdminVO;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/users")
public class AdminUserController {

    private final AdminUserService adminUserService;

    public AdminUserController(AdminUserService adminUserService) {
        this.adminUserService = adminUserService;
    }

    /** 用户列表（分页 + 关键词/状态筛选） */
    @GetMapping
    public Result<Page<UserAdminVO>> list(@RequestParam(defaultValue = "1") long page,
                                          @RequestParam(defaultValue = "10") long size,
                                          @RequestParam(required = false) String keyword,
                                          @RequestParam(required = false) Integer status,
                                          @RequestParam(required = false) Integer role) {
        return Result.ok(adminUserService.list(page, size, keyword, status, role));
    }

    /** 启用/禁用用户：body {"status": 0/1} */
    @PutMapping("/{id}/status")
    public Result<Void> updateStatus(@PathVariable Long id, @Valid @RequestBody UserStatusDTO dto) {
        adminUserService.updateStatus(id, dto.getStatus());
        return Result.ok();
    }

    /** 变更角色（1 普通用户 / 2 管理员），仅超级管理员 */
    @PutMapping("/{id}/role")
    public Result<Void> updateRole(@PathVariable Long id, @RequestBody RoleDTO dto) {
        adminUserService.updateRole(id, dto.getRole());
        return Result.ok();
    }

    /** 删除用户 */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        adminUserService.delete(id);
        return Result.ok();
    }

    /** 重置密码：body {"password":"可选，默认 123456"} */
    @PutMapping("/{id}/reset-password")
    public Result<Void> resetPassword(@PathVariable Long id, @RequestBody(required = false) ResetPasswordDTO dto) {
        adminUserService.resetPassword(id, dto == null ? null : dto.getPassword());
        return Result.ok();
    }
}
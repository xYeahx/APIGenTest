package com.apigentest.service.impl;

import com.apigentest.common.BusinessException;
import com.apigentest.common.ErrorCode;
import com.apigentest.common.Roles;
import com.apigentest.common.UserContext;
import com.apigentest.entity.Project;
import com.apigentest.entity.ProjectMember;
import com.apigentest.entity.User;
import com.apigentest.mapper.ProjectMapper;
import com.apigentest.mapper.ProjectMemberMapper;
import com.apigentest.mapper.UserMapper;
import com.apigentest.service.AdminUserService;
import com.apigentest.service.AuditService;
import com.apigentest.vo.UserAdminVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AdminUserServiceImpl implements AdminUserService {

    private static final String DEFAULT_PASSWORD = "123456";

    private final UserMapper userMapper;
    private final ProjectMapper projectMapper;
    private final ProjectMemberMapper memberMapper;
    private final BCryptPasswordEncoder passwordEncoder;
    private final AuditService auditService;

    public AdminUserServiceImpl(UserMapper userMapper, ProjectMapper projectMapper, ProjectMemberMapper memberMapper,
                                BCryptPasswordEncoder passwordEncoder, AuditService auditService) {
        this.userMapper = userMapper;
        this.memberMapper = memberMapper;
        this.projectMapper = projectMapper;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
    }

    @Override
    public Page<UserAdminVO> list(long page, long size, String keyword, Integer status, Integer role) {
        checkAdmin();
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        Integer myRole = UserContext.getRole();
        if (Roles.isSuperAdmin(myRole)) {
            // 超级管理员可见全部用户，可按角色筛选
            if (role != null) {
                wrapper.eq(User::getRole, role);
            }
        } else {
            // 管理员仅可见普通用户
            wrapper.eq(User::getRole, Roles.USER);
        }
        if (keyword != null && !keyword.isBlank()) {
            wrapper.and(w -> w.like(User::getUsername, keyword.trim())
                    .or().like(User::getNickname, keyword.trim()));
        }
        if (status != null) {
            wrapper.eq(User::getStatus, status);
        }
        wrapper.orderByDesc(User::getId);
        Page<User> userPage = userMapper.selectPage(new Page<>(page, size), wrapper);
        Page<UserAdminVO> voPage = new Page<>(userPage.getCurrent(), userPage.getSize(), userPage.getTotal());
        voPage.setRecords(userPage.getRecords().stream().map(this::toVO).toList());
        return voPage;
    }

    @Override
    public void updateStatus(Long id, Integer status) {
        checkAdmin();
        User target = requireUser(id);
        ensureManageable(target);
        if (status != null && status == 0
                && target.getRole() != null && target.getRole() == Roles.SUPER_ADMIN
                && enabledSuperAdminCount() <= 1) {
            throw new BusinessException(ErrorCode.ILLEGAL_STATE, "至少保留一个启用的超级管理员账号");
        }
        target.setStatus(status != null && status == 1 ? 1 : 0);
        userMapper.updateById(target);
        auditService.log("UPDATE_USER_STATUS", "user:" + id,
                "username=" + target.getUsername() + ", status=" + target.getStatus());
    }

    @Override
    public void resetPassword(Long id, String password) {
        checkAdmin();
        User target = requireUser(id);
        ensureManageable(target);
        String pwd = password == null || password.isBlank() ? DEFAULT_PASSWORD : password.trim();
        if (pwd.length() < 6 || pwd.length() > 20) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "密码长度需为 6~20 位");
        }
        target.setPassword(passwordEncoder.encode(pwd));
        userMapper.updateById(target);
        auditService.log("RESET_PASSWORD", "user:" + id,
                "username=" + target.getUsername() + ", 重置为" + (DEFAULT_PASSWORD.equals(pwd) ? "默认密码" : "自定义密码"));
    }

    @Override
    public void delete(Long id) {
        checkAdmin();
        User target = requireUser(id);
        ensureManageable(target);
        Long projectCount = projectMapper.selectCount(
                new LambdaQueryWrapper<Project>().eq(Project::getOwnerId, id));
        if (projectCount != null && projectCount > 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "该用户名下存在 " + projectCount + " 个项目，请先删除项目");
        }
        // 清理该用户参与的项目成员关系（外键 fk_member_user）
        memberMapper.delete(new LambdaQueryWrapper<ProjectMember>().eq(ProjectMember::getUserId, id));
        userMapper.deleteById(id);
        auditService.log("DELETE_USER", "user:" + id, "username=" + target.getUsername());
    }

    @Override
    public void updateRole(Long id, Integer role) {
        checkSuperAdmin();
        User target = requireUser(id);
        if (target.getId().equals(UserContext.getUserId())) {
            throw new BusinessException(ErrorCode.ILLEGAL_STATE, "不能修改自己的角色");
        }
        if (target.getRole() != null && target.getRole() == Roles.SUPER_ADMIN) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "不能修改超级管理员的角色");
        }
        if (role == null || (role != Roles.USER && role != Roles.ADMIN)) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "目标角色仅支持 1 普通用户 / 2 管理员");
        }
        target.setRole(role);
        userMapper.updateById(target);
        auditService.log("UPDATE_USER_ROLE", "user:" + id,
                "username=" + target.getUsername() + ", role=" + role);
    }

    /** 入口校验：管理员及以上（>=2）可进入用户管理 */
    private void checkAdmin() {
        Integer role = UserContext.getRole();
        if (!Roles.isAdmin(role)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "仅管理员或超级管理员可操作用户管理");
        }
    }

    /** 仅超级管理员（==3）可进行角色分配 */
    private void checkSuperAdmin() {
        Integer role = UserContext.getRole();
        if (!Roles.isSuperAdmin(role)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "仅超级管理员可进行角色管理");
        }
    }

    /** 层级保护：不能操作自己，不能操作同级或更高级别的账号 */
    private void ensureManageable(User target) {
        Integer myRole = UserContext.getRole();
        if (target.getId().equals(UserContext.getUserId())) {
            throw new BusinessException(ErrorCode.ILLEGAL_STATE, "不能操作自己的账号");
        }
        if (target.getRole() != null && target.getRole() >= myRole) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权操作同级或更高级别的账号");
        }
    }

    private User requireUser(Long id) {
        User user = userMapper.selectById(id);
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "用户不存在");
        }
        return user;
    }

    private long enabledSuperAdminCount() {
        return userMapper.selectCount(new LambdaQueryWrapper<User>()
                .eq(User::getRole, Roles.SUPER_ADMIN).eq(User::getStatus, 1));
    }

    private UserAdminVO toVO(User user) {
        UserAdminVO vo = new UserAdminVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setNickname(user.getNickname());
        vo.setAvatarUrl(user.getAvatarUrl());
        vo.setEmail(user.getEmail());
        vo.setPhone(user.getPhone());
        vo.setRole(user.getRole());
        vo.setStatus(user.getStatus());
        vo.setCreatedAt(user.getCreatedAt());
        return vo;
    }
}

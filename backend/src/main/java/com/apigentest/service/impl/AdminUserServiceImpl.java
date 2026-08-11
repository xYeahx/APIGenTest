package com.apigentest.service.impl;

import com.apigentest.common.BusinessException;
import com.apigentest.common.UserContext;
import com.apigentest.entity.Project;
import com.apigentest.entity.User;
import com.apigentest.mapper.ProjectMapper;
import com.apigentest.mapper.UserMapper;
import com.apigentest.service.AdminUserService;
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
    private final BCryptPasswordEncoder passwordEncoder;

    public AdminUserServiceImpl(UserMapper userMapper, ProjectMapper projectMapper,
                                BCryptPasswordEncoder passwordEncoder) {
        this.userMapper = userMapper;
        this.projectMapper = projectMapper;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public Page<UserAdminVO> list(long page, long size, String keyword, Integer status) {
        checkAdmin();
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
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
        User user = requireUser(id);
        if (user.getId().equals(UserContext.getUserId())) {
            throw new BusinessException(400, "不能禁用/启用自己");
        }
        if (status != null && status == 0) {
            if (user.getRole() != null && user.getRole() == 2 && enabledAdminCount() <= 1) {
                throw new BusinessException(400, "至少保留一个启用的管理员账号");
            }
        }
        user.setStatus(status != null && status == 1 ? 1 : 0);
        userMapper.updateById(user);
    }

    @Override
    public void resetPassword(Long id, String password) {
        checkAdmin();
        User user = requireUser(id);
        String pwd = password == null || password.isBlank() ? DEFAULT_PASSWORD : password.trim();
        if (pwd.length() < 6 || pwd.length() > 20) {
            throw new BusinessException(400, "密码长度需为 6~20 位");
        }
        user.setPassword(passwordEncoder.encode(pwd));
        userMapper.updateById(user);
    }

    @Override
    public void delete(Long id) {
        checkAdmin();
        User user = requireUser(id);
        if (user.getId().equals(UserContext.getUserId())) {
            throw new BusinessException(400, "不能删除自己");
        }
        Long projectCount = projectMapper.selectCount(
                new LambdaQueryWrapper<Project>().eq(Project::getOwnerId, id));
        if (projectCount != null && projectCount > 0) {
            throw new BusinessException(400, "该用户名下存在 " + projectCount + " 个项目，请先删除项目");
        }
        userMapper.deleteById(id);
    }

    private void checkAdmin() {
        Integer role = UserContext.getRole();
        if (role == null || role != 2) {
            throw new BusinessException(403, "仅管理员可操作用户管理");
        }
    }

    private User requireUser(Long id) {
        User user = userMapper.selectById(id);
        if (user == null) {
            throw new BusinessException(404, "用户不存在");
        }
        return user;
    }

    private long enabledAdminCount() {
        return userMapper.selectCount(new LambdaQueryWrapper<User>()
                .eq(User::getRole, 2).eq(User::getStatus, 1));
    }

    private UserAdminVO toVO(User user) {
        UserAdminVO vo = new UserAdminVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setNickname(user.getNickname());
        vo.setRole(user.getRole());
        vo.setStatus(user.getStatus());
        vo.setCreatedAt(user.getCreatedAt());
        return vo;
    }
}
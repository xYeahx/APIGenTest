package com.apigentest.service;

import com.apigentest.vo.UserAdminVO;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

/**
 * 用户管理（三级权限）：超级管理员管理管理员与普通用户，管理员管理普通用户
 */
public interface AdminUserService {

    Page<UserAdminVO> list(long page, long size, String keyword, Integer status, Integer role);

    void updateStatus(Long id, Integer status);

    void resetPassword(Long id, String password);

    /** 删除用户（用户不存在项目等业务数据时） */
    void delete(Long id);

    /** 变更用户角色（1 普通用户 / 2 管理员），仅超级管理员 */
    void updateRole(Long id, Integer role);
}
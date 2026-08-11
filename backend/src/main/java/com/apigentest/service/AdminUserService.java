package com.apigentest.service;

import com.apigentest.vo.UserAdminVO;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

/**
 * 管理员用户管理：用户列表 / 启用禁用 / 重置密码
 */
public interface AdminUserService {

    Page<UserAdminVO> list(long page, long size, String keyword, Integer status);

    void updateStatus(Long id, Integer status);

    void resetPassword(Long id, String password);

    /** 删除用户（用户不存在项目等业务数据时） */
    void delete(Long id);
}
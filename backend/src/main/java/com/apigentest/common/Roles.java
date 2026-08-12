package com.apigentest.common;

/**
 * 系统角色：三级权限体系
 * 1 普通用户 / 2 管理员（管理普通用户）/ 3 超级管理员（管理管理员与普通用户）
 * 权限规则：可管理角色严格低于自己的用户；不能操作自己；超级管理员不可被管理。
 */
public final class Roles {

    public static final int USER = 1;
    public static final int ADMIN = 2;
    public static final int SUPER_ADMIN = 3;

    private Roles() {
    }

    /** 是否管理员及以上（>=2） */
    public static boolean isAdmin(Integer role) {
        return role != null && role >= ADMIN;
    }

    /** 是否超级管理员（==3） */
    public static boolean isSuperAdmin(Integer role) {
        return role != null && role == SUPER_ADMIN;
    }
}
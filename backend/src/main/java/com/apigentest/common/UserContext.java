package com.apigentest.common;

/**
 * 当前登录用户上下文（请求级 ThreadLocal，拦截器写入，处理完清理）
 */
public class UserContext {

    private static final ThreadLocal<Long> USER_ID = new ThreadLocal<>();
    private static final ThreadLocal<String> USERNAME = new ThreadLocal<>();
    private static final ThreadLocal<Integer> ROLE = new ThreadLocal<>();

    public static void set(Long userId, String username, Integer role) {
        USER_ID.set(userId);
        USERNAME.set(username);
        ROLE.set(role);
    }

    public static Long getUserId() {
        return USER_ID.get();
    }

    public static String getUsername() {
        return USERNAME.get();
    }

    public static Integer getRole() {
        return ROLE.get();
    }

    public static void clear() {
        USER_ID.remove();
        USERNAME.remove();
        ROLE.remove();
    }
}
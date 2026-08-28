package com.apigentest.common;

/**
 * 统一业务错误码。
 * 分段约定：1xxxx 系统 / 2xxxx 参数与资源 / 3xxxx 认证与权限 /
 *           4xxxx 业务状态 / 5xxxx 第三方依赖(LLM) / 6xxxx 执行引擎
 * 约定：Result.code=0 成功；非 0 为业务错误码（前端按 30001 判定登录失效）
 */
public enum ErrorCode {

    // 1xxxx 系统类
    SYSTEM_ERROR(10001, "系统内部异常"),
    IO_ERROR(10002, "文件读写失败"),

    // 2xxxx 参数与资源
    PARAM_INVALID(20001, "参数或请求校验失败"),
    NOT_FOUND(20002, "资源不存在"),

    // 3xxxx 认证与权限
    UNAUTHORIZED(30001, "未登录或登录已过期"),
    FORBIDDEN(30002, "无权限执行该操作"),
    ACCOUNT_DISABLED(30003, "账号已被禁用"),
    INVALID_CREDENTIALS(30004, "用户名或密码错误"),

    // 4xxxx 业务状态
    ILLEGAL_STATE(40001, "当前业务状态不允许该操作"),
    CONFLICT(40002, "数据冲突或重复"),

    // 5xxxx 第三方依赖（LLM）
    LLM_CALL_FAILED(50001, "LLM 调用失败"),
    LLM_VALIDATE_FAILED(50002, "LLM 输出校验失败"),
    LLM_TIMEOUT(50003, "LLM 调用超时"),
    LLM_NOT_CONFIGURED(50004, "LLM 未配置"),

    // 6xxxx 执行引擎
    EXECUTION_FAILED(60001, "用例执行失败"),
    ENV_NOT_CONFIGURED(60002, "环境配置错误");

    private final int code;
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}
package cn.edu.medplatform.common;

/** 业务错误码 */
public enum ErrorCode {
    VALIDATION_ERROR(40001, "参数校验失败"),
    INVALID_PARAM(40001, "参数错误"),
    UNAUTHORIZED(40101, "未登录或令牌过期"),
    ACCOUNT_LOCKED(40102, "账号已被锁定"),
    FORBIDDEN(40301, "无权限"),
    DATA_FORBIDDEN(40302, "无权访问该数据"),
    NOT_FOUND(40401, "资源不存在"),
    STATE_CONFLICT(40901, "状态不允许此操作"),
    DUPLICATE_SUBMIT(40902, "重复提交"),
    AI_SERVICE_ERROR(50301, "AI 服务不可用"),
    INTERNAL_ERROR(50001, "服务异常");

    public final int code;
    public final String message;
    ErrorCode(int code, String message) { this.code = code; this.message = message; }
}

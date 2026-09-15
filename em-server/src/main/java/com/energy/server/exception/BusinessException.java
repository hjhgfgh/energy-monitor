package com.energy.server.exception;

/**
 * 业务异常。
 *
 * <p>与系统异常区分开：业务异常是「可预期的、需要告知用户原因」的失败（如设备不存在），
 * 这类异常的堆栈信息没有价值，也不应该打印完整堆栈；系统异常才需要完整堆栈定位。
 */
public class BusinessException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final int code;

    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }

    public BusinessException(String message) {
        this(com.energy.server.dto.Result.CODE_BUSINESS_ERROR, message);
    }

    public int getCode() {
        return code;
    }
}

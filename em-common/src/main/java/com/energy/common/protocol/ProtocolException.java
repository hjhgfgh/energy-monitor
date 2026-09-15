package com.energy.common.protocol;

/**
 * 协议解析异常。出现魔数不匹配、CRC 校验失败、载荷长度非法等情况时抛出。
 */
public class ProtocolException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public ProtocolException(String message) {
        super(message);
    }

    public ProtocolException(String message, Throwable cause) {
        super(message, cause);
    }
}

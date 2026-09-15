package com.energy.server.dto;

import lombok.Data;

/**
 * 统一响应体。
 *
 * <p>所有接口都返回同一结构，前端只需判断一次 {@code code}，无需为每个接口写不同的解析逻辑。
 */
@Data
public class Result<T> {

    public static final int CODE_SUCCESS = 0;
    public static final int CODE_BUSINESS_ERROR = 1000;
    public static final int CODE_PARAM_ERROR = 1001;
    public static final int CODE_NOT_FOUND = 4004;
    public static final int CODE_SERVER_ERROR = 5000;

    private int code;
    private String message;
    private T data;
    private long timestamp;

    public static <T> Result<T> ok(T data) {
        Result<T> result = new Result<>();
        result.setCode(CODE_SUCCESS);
        result.setMessage("success");
        result.setData(data);
        result.setTimestamp(System.currentTimeMillis());
        return result;
    }

    public static <T> Result<T> fail(int code, String message) {
        Result<T> result = new Result<>();
        result.setCode(code);
        result.setMessage(message);
        result.setTimestamp(System.currentTimeMillis());
        return result;
    }
}

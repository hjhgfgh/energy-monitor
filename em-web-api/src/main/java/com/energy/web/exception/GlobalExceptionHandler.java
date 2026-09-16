package com.energy.common.exception;

import com.energy.common.dto.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;

import java.util.stream.Collectors;

/**
 * 全局异常处理。
 *
 * <p>分三类处理，粒度不同：
 * <ul>
 *   <li>业务异常：可预期，只打一行 warn，不输出堆栈</li>
 *   <li>参数异常：客户端问题，返回具体哪个参数错了，便于前端定位</li>
 *   <li>系统异常：服务端问题，完整堆栈进日志，但对客户端只返回笼统提示——
 *       不把内部异常信息（SQL、类名、路径）泄露给外部</li>
 * </ul>
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 路由匹配失败：路径不存在，或路径变量不满足正则约束（如 {@code /api/devices/abc}）。
     *
     * <p><b>必须显式处理</b>，否则会被下面的兜底 {@code Exception} 分支捕获，
     * 把「路径不存在」报成「服务器内部错误」——既误导前端排查方向，
     * 也会让错误告警里塞满无意义的 5xx。
     */
    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<Result<Void>> handleNotFound(NoHandlerFoundException e) {
        log.warn("请求路径不存在: {} {}", e.getHttpMethod(), e.getRequestURL());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Result.fail(Result.CODE_NOT_FOUND, "请求路径不存在"));
    }

    @ExceptionHandler(BusinessException.class)
    public Result<Void> handleBusiness(BusinessException e) {
        log.warn("业务异常: code={}, message={}", e.getCode(), e.getMessage());
        return Result.fail(e.getCode(), e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Void> handleValidation(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(field -> field.getField() + " " + field.getDefaultMessage())
                .collect(Collectors.joining("; "));
        log.warn("参数校验失败: {}", message);
        return Result.fail(Result.CODE_PARAM_ERROR, message);
    }

    @ExceptionHandler({
            MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class,
            HttpMessageNotReadableException.class
    })
    public Result<Void> handleBadRequest(Exception e) {
        log.warn("请求参数错误: {}", e.getMessage());
        return Result.fail(Result.CODE_PARAM_ERROR, "请求参数错误");
    }

    @ExceptionHandler(Exception.class)
    public Result<Void> handleUnexpected(Exception e) {
        log.error("系统异常", e);
        return Result.fail(Result.CODE_SERVER_ERROR, "服务器内部错误，请稍后重试");
    }
}

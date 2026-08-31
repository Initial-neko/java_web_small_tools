package com.toolbox.api;

import com.toolbox.core.ToolResult;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理，避免堆栈直接抛到前端。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(Exception.class)
    public ToolResult handle(Exception e) {
        return ToolResult.fail(e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
    }
}

package com.toolbox.core;

/**
 * 工具执行结果统一包装。
 */
public class ToolResult {

    private boolean success;
    private String message;
    private Object data;

    public ToolResult() {
    }

    public ToolResult(boolean success, String message, Object data) {
        this.success = success;
        this.message = message;
        this.data = data;
    }

    public static ToolResult ok(Object data) {
        return new ToolResult(true, null, data);
    }

    public static ToolResult ok(String message, Object data) {
        return new ToolResult(true, message, data);
    }

    public static ToolResult fail(String message) {
        return new ToolResult(false, message, null);
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Object getData() {
        return data;
    }

    public void setData(Object data) {
        this.data = data;
    }
}

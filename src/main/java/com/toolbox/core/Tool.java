package com.toolbox.core;

import java.util.Map;

/**
 * 工具统一接口。新增工具只需实现此接口并加 @Component，自动注册。
 */
public interface Tool {

    /** 工具唯一标识，如 "timestamp" */
    String getName();

    /** 前端展示名 */
    String getDisplayName();

    /** 功能说明 */
    String getDescription();

    /**
     * 执行工具
     * @param params 前端传入的参数
     * @return 统一返回结果
     */
    ToolResult execute(Map<String, Object> params);
}

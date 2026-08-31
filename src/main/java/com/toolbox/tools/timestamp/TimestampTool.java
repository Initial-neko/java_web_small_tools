package com.toolbox.tools.timestamp;

import com.toolbox.core.Tool;
import com.toolbox.core.ToolResult;
import org.springframework.stereotype.Component;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.TimeZone;

/**
 * 时间戳转换工具。
 * 支持：时间戳→日期、日期→时间戳、获取当前时间戳。
 * 自动识别秒级/毫秒级。
 */
@Component
public class TimestampTool implements Tool {

    @Override
    public String getName() {
        return "timestamp";
    }

    @Override
    public String getDisplayName() {
        return "时间戳转换";
    }

    @Override
    public String getDescription() {
        return "时间戳与日期互转，自动识别秒/毫秒，支持自定义格式与时区";
    }

    @Override
    public ToolResult execute(Map<String, Object> params) {
        String type = str(params.get("type"), "now");
        String format = str(params.get("format"), "yyyy-MM-dd HH:mm:ss");
        String timezone = str(params.get("timezone"), null);

        try {
            SimpleDateFormat sdf = new SimpleDateFormat(format);
            if (timezone != null && !timezone.isEmpty()) {
                sdf.setTimeZone(TimeZone.getTimeZone(timezone));
            }

            Map<String, Object> data = new HashMap<String, Object>();

            if ("now".equals(type)) {
                long now = System.currentTimeMillis();
                data.put("timestampMs", now);
                data.put("timestampSec", now / 1000);
                data.put("datetime", sdf.format(new Date(now)));
                return ToolResult.ok(data);
            }

            if ("toDate".equals(type)) {
                String tsStr = str(params.get("timestamp"), null);
                if (tsStr == null || tsStr.isEmpty()) {
                    return ToolResult.fail("timestamp 不能为空");
                }
                long ts = Long.parseLong(tsStr.trim());
                // 自动识别：小于等于10位视为秒，否则视为毫秒
                if (tsStr.trim().length() <= 10) {
                    ts = ts * 1000;
                }
                data.put("datetime", sdf.format(new Date(ts)));
                data.put("timestampMs", ts);
                data.put("timestampSec", ts / 1000);
                return ToolResult.ok(data);
            }

            if ("toTimestamp".equals(type)) {
                String datetime = str(params.get("datetime"), null);
                if (datetime == null || datetime.isEmpty()) {
                    return ToolResult.fail("datetime 不能为空");
                }
                Date date = sdf.parse(datetime);
                long ms = date.getTime();
                data.put("timestampMs", ms);
                data.put("timestampSec", ms / 1000);
                data.put("datetime", sdf.format(date));
                return ToolResult.ok(data);
            }

            return ToolResult.fail("未知 type: " + type + "，可选 now/toDate/toTimestamp");
        } catch (Exception e) {
            return ToolResult.fail("转换失败: " + e.getMessage());
        }
    }

    private String str(Object obj, String def) {
        return obj == null ? def : obj.toString();
    }
}

package com.intelligent_data_analysis_system.utils;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class AnswerRenderer {

    public String render(String problem, List<Map<String, Object>> rows) {
        if (rows == null || rows.isEmpty()) {
            return "无法查询到结果";
        }

        // 1行1列：直接输出值（最像标准答案）
        if (rows.size() == 1 && rows.get(0).size() == 1) {
            Object v = rows.get(0).values().iterator().next();
            return v == null ? "NULL" : String.valueOf(v);
        }

        // 否则：输出前10行简表（别太长）
        int limit = Math.min(rows.size(), 10);
        StringBuilder sb = new StringBuilder();
        sb.append("结果如下（前").append(limit).append("行）：\n");
        for (int i = 0; i < limit; i++) {
            sb.append(i + 1).append(". ");
            Map<String, Object> r = rows.get(i);
            int c = 0;
            for (Map.Entry<String, Object> e : r.entrySet()) {
                if (c >= 6) break; // 每行最多 6 列，防止太长
                sb.append(e.getKey()).append("=").append(e.getValue());
                sb.append(c == r.size() - 1 ? "" : "; ");
                c++;
            }
            sb.append("\n");
        }
        return sb.toString().trim();
    }
}
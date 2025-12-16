package com.intelligent_data_analysis_system.controller;

import com.intelligent_data_analysis_system.service.AiText2SqlService;
import com.intelligent_data_analysis_system.service.SqlExecuteService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AiQueryController {

    private final AiText2SqlService aiText2SqlService;
    private final SqlExecuteService sqlExecuteService;

    /**
     * 输入自然语言，输出：生成SQL + 执行结果
     * body: { "question": "..." }
     */
    @PostMapping("/query")
    public Map<String, Object> query(@RequestBody Map<String, Object> body) {
        String question = body == null ? null : String.valueOf(body.get("question"));

        // 1) NL -> {domain, dbms, sql, maxRows}
        Map<String, Object> plan = aiText2SqlService.nl2sql(question);

        // 2) 执行（复用已跑通的统一执行服务）
        Map<String, Object> execBody = new LinkedHashMap<>();
        execBody.put("domain", plan.get("domain"));
        execBody.put("dbms", plan.get("dbms"));
        execBody.put("sql", plan.get("sql"));
        execBody.put("maxRows", plan.getOrDefault("maxRows", 200));

        Map<String, Object> result = sqlExecuteService.execute(execBody);

        // 3) 返回：既给看“生成SQL”，也给前端用“结果”
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("question", question);
        resp.put("generated", plan);
        resp.put("result", result);
        return resp;
    }
}

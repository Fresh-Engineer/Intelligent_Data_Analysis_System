package com.intelligent_data_analysis_system.utils.Generator;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "app.ai.mode", havingValue = "stub", matchIfMissing = true)
public class StubSqlGenerator implements SqlGenerator {

    @Override
    public SqlGenResult generate(String domain, String problem) {
        // Stub 模式：
        // 1) domain 由 runner 决定，这里不再猜
        // 2) 不生成 SQL，留空，让 BatchRunner 进入 pred_sql 为空的 fail 分支
        return new SqlGenResult(domain, "");
    }
}

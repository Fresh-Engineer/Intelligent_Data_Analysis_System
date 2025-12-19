package com.intelligent_data_analysis_system.utils;

import com.intelligent_data_analysis_system.infrastructure.config.properties.RoutingProperties;
import com.intelligent_data_analysis_system.service.SqlSelfCheckService;
import com.intelligent_data_analysis_system.utils.Normalizer.EnumValueNormalizer;
import com.intelligent_data_analysis_system.utils.Normalizer.SqlValueNormalizer;
import com.intelligent_data_analysis_system.utils.Normalizer.YearMonthNormalizer;

public class SqlPatchPipeline {

    public static String apply(
            String domain,
            String problem,
            String sql,
            SqlSelfCheckService.CheckResult check
    ) {
        if (sql == null || sql.isBlank()) return sql;

        String out = sql;

        RoutingProperties routingProperties = new RoutingProperties();
        String dbms = routingProperties.getDbms();

        // 1️⃣ 低风险：值归一化（已存在）
        out = SqlValueNormalizer.normalize(out);
        out = EnumValueNormalizer.normalize(domain, out);
        out = YearMonthNormalizer.apply(dbms, domain, problem, out, null);


        // 2️⃣ 中风险：WHERE 注入（必须 gating）
        if (WherePresenceGate.shouldInject(problem, check)) {
            var ow = ConstraintExtractor.safeWhere(domain, problem);
            if (ow.isPresent()) {
                String patched = SqlWherePatcher.addConditionSafely(out, ow.get());
                if (patched != null && !patched.isBlank()) {
                    out = patched;
                }
            }
        }

        return out;
    }
}

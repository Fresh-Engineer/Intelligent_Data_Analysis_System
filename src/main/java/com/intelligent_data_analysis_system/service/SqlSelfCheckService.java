package com.intelligent_data_analysis_system.service;

import org.springframework.stereotype.Service;

@Service
public class SqlSelfCheckService {

    public static class CheckResult {
        public final boolean ok;
        public final String hint;

        private CheckResult(boolean ok, String hint) {
            this.ok = ok;
            this.hint = hint;
        }

        public static CheckResult ok() {
            return new CheckResult(true, "");
        }

        public static CheckResult fail(String hint) {
            return new CheckResult(false, hint);
        }
    }

    public CheckResult check(String problem, String sql) {
        if (problem == null || sql == null) {
            return CheckResult.ok();
        }

        String p = problem.toLowerCase();
        String s = sql.toLowerCase();

        // ===== A. 统计类 =====
        if (containsAny(p, "多少", "数量", "总数", "统计", "次数", "count")) {
            if (!(s.contains("count(") || s.contains("sum(") || s.contains("avg("))) {
                return CheckResult.fail("题目是统计类，但 SQL 缺少 COUNT/SUM/AVG 等聚合函数");
            }
        }

        // ===== B. 分组类 =====
        if (containsAny(p, "每个", "各", "按", "分别", "分组")) {
            if (!s.contains("group by")) {
                return CheckResult.fail("题目是分组统计，但 SQL 缺少 GROUP BY");
            }
        }

        // ===== C. Top / 极值类 =====
        if (containsAny(p, "前", "top", "最高", "最大", "最小", "最多", "最少")) {
            if (!s.contains("order by")) {
                return CheckResult.fail("题目涉及排序/极值，但 SQL 缺少 ORDER BY");
            }
        }

        // ===== D. finance 列表类：强制关键列 =====
        if (isFinanceListingClients(problem, s)) {
            // 题目是“查看/列出/查询 客户 ...”的列表类
            // 要求至少包含 client_id/client_name/risk_level（你也可以把 total_assets 设为必须）
            if (!(s.contains("client_id") && s.contains("client_name") && s.contains("risk_level"))) {
                return CheckResult.fail("查看客户列表需返回 client_id, client_name, risk_level, total_assets 等关键列（不要只返回单列）");
            }
        }

        // ===== E. healthcare：患者列表类 =====
        if (isHealthcareListingPatients(problem, s)) {
            if (!(s.contains("patient_id") && s.contains("name"))) {
                return CheckResult.fail("查看患者列表需返回 patient_id, name, gender, age 等关键列");
            }
        }



        return CheckResult.ok();
    }

    private boolean containsAny(String text, String... keys) {
        for (String k : keys) {
            if (text.contains(k)) return true;
        }
        return false;
    }

    private boolean isFinanceListingClients(String problem, String lowerSql) {
        if (problem == null) return false;
        String p = problem.toLowerCase();
        boolean verb = containsAny(p, "查看", "查询", "列出", "显示", "获取", "找出");
        boolean obj = p.contains("客户");
        boolean fromClients = lowerSql.contains("from clients");
        boolean noAgg = !(lowerSql.contains("count(") || lowerSql.contains("sum(") || lowerSql.contains("avg(")
                || lowerSql.contains("min(") || lowerSql.contains("max(") || lowerSql.contains("group by"));
        return verb && obj && fromClients && noAgg;
    }

    private boolean isHealthcareListingPatients(String problem, String lowerSql) {
        if (problem == null) return false;
        String p = problem.toLowerCase();
        boolean verb = containsAny(p, "查看", "查询", "列出", "显示", "获取", "找出");
        boolean obj = p.contains("患者");
        boolean fromPatients = lowerSql.contains("from patients");
        boolean noAgg = !(lowerSql.contains("count(") || lowerSql.contains("group by"));
        return verb && obj && fromPatients && noAgg;
    }


}

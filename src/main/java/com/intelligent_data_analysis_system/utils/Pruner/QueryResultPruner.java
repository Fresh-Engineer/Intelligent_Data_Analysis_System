package com.intelligent_data_analysis_system.utils.Pruner;

import com.intelligent_data_analysis_system.infrastructure.runner.dto.QueryResult;
import com.intelligent_data_analysis_system.utils.SelectIntentRouter;

import java.util.*;

public class QueryResultPruner {

    // 常见聚合别名
    private static final Set<String> AGG_NAMES = Set.of(
            "cnt","count","total","sum","avg","min","max",
            "num","number","amount","value"
    );

    public static QueryResult pruneByIntent(String domain, String problem, QueryResult qr) {
        if (qr == null || qr.getColumns() == null || qr.getRows() == null) return qr;
        if (qr.getColumns().isEmpty()) return qr;
        if (qr.getColumns().size() == 1) return qr;

        // 1) 统计/数量/总和/平均…：只保留聚合列（或第一列）
        if (SelectIntentRouter.wantAggregate(problem)) {
            return keepAggColumnsOrFirst(qr);
        }

        // 2) “只要某一个字段”：按关键词映射保留
        if (SelectIntentRouter.wantSingleField(problem)) {
            Set<String> keep = pickSingleFieldKeepSet(domain, problem);
            if (!keep.isEmpty()) {
                QueryResult out = keepColumns(qr, keep);
                if (out.getColumns() != null && !out.getColumns().isEmpty()) return out;
            }
        }

        // 3) 兜底：不裁剪
        return qr;
    }

    /** 聚合类：优先保留名字像 cnt/sum/avg... 的列；若没有，就保留第一列 */
    private static QueryResult keepAggColumnsOrFirst(QueryResult qr) {
        List<String> cols = qr.getColumns();
        Set<String> keep = new LinkedHashSet<>();
        for (String c : cols) {
            String lc = c == null ? "" : c.toLowerCase(Locale.ROOT);
            if (AGG_NAMES.contains(lc) || lc.startsWith("count") || lc.startsWith("sum")
                    || lc.startsWith("avg") || lc.startsWith("min") || lc.startsWith("max")) {
                keep.add(lc);
            }
        }
        if (keep.isEmpty()) {
            // 没识别到聚合列名：保留第一列
            keep.add(cols.get(0).toLowerCase(Locale.ROOT));
        }
        return keepColumns(qr, keep);
    }

    /** 单字段类：根据题干关键词决定要保留哪一列（跨域通用 + 域内少量映射） */
    private static Set<String> pickSingleFieldKeepSet(String domain, String problem) {
        String p = problem == null ? "" : problem.toLowerCase(Locale.ROOT);
        Set<String> keep = new LinkedHashSet<>();

        // 通用：姓名/名称/编号/ID/日期/金额/资产/风险等级/性别/年龄…
        if (containsAny(p, "姓名")) keep.add("client_name"); // finance 常见
        if (containsAny(p, "名称", "名字")) {
            keep.add("product_name");
            keep.add("name"); // healthcare 常见
            keep.add("client_name");
        }
        if (containsAny(p, "编号", "id")) {
            keep.add("client_id");
            keep.add("patient_id");
            keep.add("product_id");
            keep.add("portfolio_id");
            keep.add("transaction_id");
            keep.add("encounter_id");
        }
        if (containsAny(p, "日期", "时间")) {
            keep.add("trade_date");
            keep.add("encounter_date");
            keep.add("order_date");
            keep.add("billing_date");
        }
        if (containsAny(p, "金额", "交易额", "费用")) {
            keep.add("transaction_amount");
            keep.add("amount");
        }
        if (containsAny(p, "资产")) keep.add("total_assets");
        if (containsAny(p, "风险等级")) keep.add("risk_level");
        if (containsAny(p, "性别")) keep.add("gender");
        if (containsAny(p, "年龄")) keep.add("age");

        // 域内：更精准一点
        if ("HEALTHCARE".equalsIgnoreCase(domain)) {
            if (containsAny(p, "患者")) {
                keep.add("patient_id");
                keep.add("name");
            }
        }
        if ("FINANCE".equalsIgnoreCase(domain)) {
            if (containsAny(p, "客户")) {
                keep.add("client_id");
                keep.add("client_name");
            }
        }

        // 注意：这里只是“希望保留的候选集合”，最终会与实际列求交
        return keep;
    }

    /** 核心：按 keep（小写列名集合）裁剪 QueryResult */
    private static QueryResult keepColumns(QueryResult qr, Set<String> keepLower) {
        List<String> cols = qr.getColumns();
        List<Integer> idx = new ArrayList<>();
        List<String> newCols = new ArrayList<>();

        for (int i = 0; i < cols.size(); i++) {
            String c = cols.get(i);
            String lc = c == null ? "" : c.toLowerCase(Locale.ROOT);
            if (keepLower.contains(lc)) {
                idx.add(i);
                newCols.add(c);
            }
        }

        // 若一个都没命中：不裁剪
        if (idx.isEmpty()) return qr;

        List<List<Object>> newRows = new ArrayList<>();
        for (List<Object> row : qr.getRows()) {
            List<Object> r = new ArrayList<>(idx.size());
            for (int j : idx) {
                r.add(j < row.size() ? row.get(j) : null);
            }
            newRows.add(r);
        }

        QueryResult out = new QueryResult();
        out.setSuccess(qr.isSuccess());
        out.setStatus(qr.getStatus());
        out.setColumns(newCols);
        out.setRows(newRows);
        return out;
    }

    private static boolean containsAny(String s, String... ks) {
        for (String k : ks) if (s.contains(k)) return true;
        return false;
    }
}

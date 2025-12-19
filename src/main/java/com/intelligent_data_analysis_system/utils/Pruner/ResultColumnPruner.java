package com.intelligent_data_analysis_system.utils.Pruner;

import com.intelligent_data_analysis_system.infrastructure.runner.dto.QueryResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class ResultColumnPruner {

    public static QueryResult prune(String domain, String problem, QueryResult qr) {
        if (qr == null || qr.getColumns() == null) return qr;
        if (qr.getColumns().size() <= 1) return qr; // 单列不裁

        // 只对“查看/查询/列出”类生效
        if (!isListing(problem)) return qr;

        Set<String> keep = expectedCols(domain, problem, qr.getColumns());
        if (keep == null || keep.isEmpty()) return qr;

        // 计算需要保留的列 index
        List<Integer> idx = new ArrayList<>();
        List<String> newCols = new ArrayList<>();
        for (int i = 0; i < qr.getColumns().size(); i++) {
            String c = qr.getColumns().get(i);
            if (keep.contains(c.toLowerCase())) {
                idx.add(i);
                newCols.add(c);
            }
        }

        // 如果裁剪后为空，放弃裁剪（避免全丢）
        if (idx.isEmpty()) return qr;

        // 裁剪 rows
        List<List<Object>> newRows = new ArrayList<>();
        for (List<Object> row : qr.getRows()) {
            List<Object> r = new ArrayList<>();
            for (int i : idx) r.add(row.get(i));
            newRows.add(r);
        }

        qr.setColumns(newCols);
        qr.setRows(newRows);
        return qr;
    }

    private static boolean isListing(String problem) {
        if (problem == null) return false;
        return problem.contains("查看") || problem.contains("查询")
                || problem.contains("列出") || problem.contains("显示");
    }

    private static Set<String> expectedCols(String domain, String problem, List<String> cols) {
        String p = problem.toLowerCase();
        if ("FINANCE".equalsIgnoreCase(domain)) {
            if (p.contains("客户")) return Set.of("client_id","client_name","risk_level","total_assets");
            if (p.contains("产品")) return Set.of("product_id","product_name","product_type","risk_rating");
            if (p.contains("交易")) return Set.of("transaction_id","trade_date","transaction_amount");
            if (p.contains("组合")) return Set.of("portfolio_id","portfolio_code","current_value");
        }
        return null;
    }
}


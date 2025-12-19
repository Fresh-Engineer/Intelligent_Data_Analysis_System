package com.intelligent_data_analysis_system.utils;

public class SelectIntentRouter {

    public static boolean wantAggregate(String problem) {
        if (problem == null) return false;
        return contains(problem, "数量","多少","统计","总数","总和","平均","最大","最小");
    }

    public static boolean wantSingleField(String problem) {
        if (problem == null) return false;
        return contains(problem, "姓名","名称","编号","金额","资产");
    }

    private static boolean contains(String s, String... ks) {
        for (String k : ks) if (s.contains(k)) return true;
        return false;
    }
}

package com.intelligent_data_analysis_system.infrastructure.runner.dto;


import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class AdvancedReportItem {
    public String id;
    public String query;          // 题目文本
    public String chartType;      // "kpi" | "bar" | "pie" | "line" | "table"
    public List<String> labels;   // 类别/时间轴
    public List<Double> values;   // 单序列数值
    public String kpiText;        // chartType=kpi 时展示（可选）
    public String summaryHtml;    // 结论性文字（可选，非必需）

    public AdvancedReportItem(String id, String query) {
        this.id = id;
        this.query = query;
        this.labels = new ArrayList<>();
        this.values = new ArrayList<>();
        this.kpiText = "";
        this.summaryHtml = "";
        this.chartType = "table";
    }
}
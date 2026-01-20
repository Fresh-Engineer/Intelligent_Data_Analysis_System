package com.intelligent_data_analysis_system.infrastructure.runner.dto;


import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class AdvancedReportItem {
    private String id;
    private String query;

    // 文字分析（不含错误/耗时/对比）
    private String narrative;

    // 原始数据表（一定给，哪怕没有图）
    private String tableHtml;

    // 0~2 张图（内联 SVG，避免 base64 拥塞）
    private List<String> chartSvgs = new ArrayList<>();

    // 小结要点
    private List<String> bullets = new ArrayList<>();

}
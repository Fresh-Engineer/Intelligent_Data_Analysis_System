package com.intelligent_data_analysis_system.infrastructure.runner.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ProblemItem {
    private String level; // 初级/中级/高级
    private Integer id;
    private String problem;
    private String sql; // 题库里可能有，也可能没有
    private String domain; // FINANCE / HEALTHCARE
}

package com.intelligent_data_analysis_system.infrastructure.runner.dto;

import lombok.Data;

@Data
public class SubmitItem {
    private String id;
    private String query;
    private String sql;
    private String answer;

    public SubmitItem(String id, String query, String sql, String answer) {
        this.id = id;
        this.query = query;
        this.sql = sql;
        this.answer = answer;
    }
}

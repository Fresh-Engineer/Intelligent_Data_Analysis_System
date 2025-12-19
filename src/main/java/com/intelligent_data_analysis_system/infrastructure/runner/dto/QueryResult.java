package com.intelligent_data_analysis_system.infrastructure.runner.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class QueryResult {

    private boolean success;
    private String status;      // success / fail（方便写Excel）
    private String errorMsg;
    private long elapsedMs;

    private List<String> columns = new ArrayList<>();
    private List<List<Object>> rows = new ArrayList<>();

    public boolean isEmpty() {
        return rows == null || rows.isEmpty();
    }

    public int rowCount() {
        return rows == null ? 0 : rows.size();
    }

    public int colCount() {
        return columns == null ? 0 : columns.size();
    }

    public Object get(int r, int c) {
        return rows.get(r).get(c);
    }

    public Object getFirstValue() {
        if (isEmpty() || colCount() == 0) return null;
        return rows.get(0).get(0);
    }
}

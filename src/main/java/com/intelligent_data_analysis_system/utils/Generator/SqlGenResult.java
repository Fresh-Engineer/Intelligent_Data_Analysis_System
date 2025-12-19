package com.intelligent_data_analysis_system.utils.Generator;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class SqlGenResult {
    private String domain; // FINANCE / HEALTHCARE
    private String sql;
}

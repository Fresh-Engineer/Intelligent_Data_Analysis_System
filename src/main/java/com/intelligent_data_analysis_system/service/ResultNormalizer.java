package com.intelligent_data_analysis_system.service;

import com.intelligent_data_analysis_system.infrastructure.runner.dto.QueryResult;

public interface ResultNormalizer {
    String normalize(String problem, String predSql, QueryResult result);
}

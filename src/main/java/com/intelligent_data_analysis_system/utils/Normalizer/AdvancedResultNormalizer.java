package com.intelligent_data_analysis_system.utils.Normalizer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.intelligent_data_analysis_system.infrastructure.dto.ApiResponse;
import com.intelligent_data_analysis_system.infrastructure.exception.BusinessException;
import com.intelligent_data_analysis_system.infrastructure.runner.dto.QueryResult;
import com.intelligent_data_analysis_system.service.ResultNormalizer;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 高级结果规范化器
 * 提供更智能的结果处理，包括：
 * 1. 类型转换和格式化
 * 2. 空值处理
 * 3. 数值精度控制
 * 4. 数据脱敏
 * 5. 结果排序和分页
 */
@Component
@Primary
public class AdvancedResultNormalizer implements ResultNormalizer {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final int DEFAULT_MAX_DECIMAL_PLACES = 2;

    private static final Set<String> SENSITIVE_FIELDS = Set.of(
            "password", "email", "phone", "mobile", "id_card", "idcard", "social_security_number", "ssn", "credit_card",
            "bank_account", "account_number", "passport", "driver_license", "salary", "income", "address", "postal_code",
            "zip_code", "tax_id", "tin", "national_id", "birth_date", "date_of_birth", "credit_score"
    );

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String normalize(String problem, String predSql, QueryResult result) {
        try {
            // 将QueryResult转换为List<Map<String, Object>>格式
            List<Map<String, Object>> resultList = convertQueryResultToList(result);
            
            // 应用高级规范化处理
            List<Map<String, Object>> normalized = advancedNormalize(resultList, null, null, null, null, null);
            
            // 计算统计信息
            Map<String, Object> statistics = calculateStatistics(resultList);
            
            // 创建包含结果的响应数据
            Map<String, Object> responseData = new LinkedHashMap<>();
            responseData.put("status", result.getStatus());
            responseData.put("elapsedMs", result.getElapsedMs());
            responseData.put("rowCount", result.rowCount());
            responseData.put("colCount", result.colCount());
            responseData.put("columns", result.getColumns());
            responseData.put("data", normalized);
            
            // 如果有错误信息，添加到响应数据中
            if (result.getErrorMsg() != null) {
                responseData.put("errorMsg", result.getErrorMsg());
            }
            
            // 创建ApiResponse对象
            ApiResponse<Map<String, Object>> apiResponse;
            if (result.isSuccess()) {
                // 成功响应
                if (!statistics.isEmpty() && statistics.containsKey("fieldStatistics")) {
                    // 有统计信息，作为meta字段
                    apiResponse = ApiResponse.success(responseData, statistics);
                } else {
                    // 无统计信息
                    apiResponse = ApiResponse.success(responseData);
                }
            } else {
                // 失败响应
                apiResponse = ApiResponse.error(500, "查询执行失败");
                apiResponse.setData(responseData);
            }
            
            // 转换为JSON字符串
            return objectMapper.writeValueAsString(apiResponse);
        } catch (Exception e) {
            // 如果处理失败，返回包含错误信息的响应
            try {
                // 封装错误信息
                String errorMsg = "结果规范化失败: " + e.getMessage();
                ApiResponse<Object> errorResponse = ApiResponse.error(500, errorMsg);
                
                // 添加原始数据作为参考
                Map<String, Object> errorData = new LinkedHashMap<>();
                errorData.put("rawData", convertQueryResultToList(result));
                errorResponse.setData(errorData);
                
                return objectMapper.writeValueAsString(errorResponse);
            } catch (Exception ex) {
                // 如果仍然失败，返回简单的错误信息
                return "{\"code\": 500, \"message\": \"结果处理失败\"}";
            }
        }
    }

    /**
     * 将QueryResult转换为List<Map<String, Object>>格式
     */
    private List<Map<String, Object>> convertQueryResultToList(QueryResult result) {
        List<Map<String, Object>> list = new ArrayList<>();
        if (result == null || result.isEmpty()) {
            return list;
        }

        List<String> columns = result.getColumns();
        List<List<Object>> rows = result.getRows();

        for (List<Object> row : rows) {
            Map<String, Object> map = new LinkedHashMap<>();
            for (int i = 0; i < columns.size(); i++) {
                String column = columns.get(i);
                Object value = i < row.size() ? row.get(i) : null;
                map.put(column, value);
            }
            list.add(map);
        }
        return list;
    }

    /**
     * 高级结果规范化
     * @param result 原始查询结果
     * @param sortBy 排序字段
     * @param sortOrder 排序方向（asc/desc）
     * @param page 页码（从1开始）
     * @param pageSize 每页数量
     * @param maxDecimalPlaces 最大小数位数
     * @return 规范化后的结果
     */
    public List<Map<String, Object>> advancedNormalize(List<Map<String, Object>> result,
                                              String sortBy,
                                              String sortOrder,
                                              Integer page,
                                              Integer pageSize,
                                              Integer maxDecimalPlaces) {
        if (result == null || result.isEmpty()) {
            return Collections.emptyList();
        }

        List<Map<String, Object>> normalized = new ArrayList<>();

        try {
            // 1. 基本类型转换和格式化
            for (Map<String, Object> row : result) {
                if (row == null) {
                    throw new BusinessException("规范化失败：行数据为空");
                }
                Map<String, Object> normalizedRow = new LinkedHashMap<>();
                for (Map.Entry<String, Object> entry : row.entrySet()) {
                    String fieldName = entry.getKey();
                    if (fieldName == null || fieldName.isEmpty()) {
                        throw new BusinessException("规范化失败：字段名为空");
                    }
                    Object value = entry.getValue();

                    // 2. 数据脱敏
                    if (isSensitiveField(fieldName)) {
                        try {
                            value = maskSensitiveData(fieldName, String.valueOf(value));
                        } catch (Exception e) {
                            throw new BusinessException("数据脱敏失败：" + e.getMessage());
                        }
                    }

                    // 3. 类型转换和格式化
                    try {
                        value = formatValue(value, fieldName, maxDecimalPlaces);
                    } catch (Exception e) {
                        throw new BusinessException("数据格式化失败：" + e.getMessage());
                    }

                    normalizedRow.put(fieldName, value);
                }
                normalized.add(normalizedRow);
            }

            // 4. 排序
            if (sortBy != null && !sortBy.isEmpty()) {
                normalized = sortResult(normalized, sortBy, sortOrder);
            }

            // 5. 分页
            if (page != null && pageSize != null && page > 0 && pageSize > 0) {
                normalized = paginateResult(normalized, page, pageSize);
            }
        } catch (BusinessException e) {
            // 直接抛出自定义业务异常
            throw e;
        } catch (Exception e) {
            // 将其他异常转换为业务异常
            throw new BusinessException("结果规范化失败：" + e.getMessage());
        }

        return normalized;
    }

    private boolean isSensitiveField(String fieldName) {
        return SENSITIVE_FIELDS.contains(fieldName.toLowerCase());
    }

    private String maskSensitiveData(String fieldName, String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }

        String lowerField = fieldName.toLowerCase();
        // 优先处理常见敏感字段，减少字符串操作
        if (lowerField.contains("password")) {
            return "******";
        } else if (lowerField.contains("email")) {
            // 邮箱脱敏：example***@domain.com
            int atIndex = value.indexOf('@');
            if (atIndex > 0) {
                String username = value.substring(0, atIndex);
                String domain = value.substring(atIndex);
                if (username.length() <= 3) {
                    return username + "***" + domain;
                } else {
                    return username.substring(0, 3) + "***" + domain;
                }
            }
        } else if (lowerField.contains("phone") || lowerField.contains("mobile")) {
            // 手机号脱敏：138****1234
            int length = value.length();
            if (length == 11) {
                return value.substring(0, 3) + "****" + value.substring(7);
            } else if (length > 7) {
                return value.substring(0, 3) + "****" + value.substring(length - 4);
            }
        } else if (lowerField.contains("id_card") || lowerField.contains("idcard") || lowerField.contains("national_id")) {
            // 身份证号脱敏：110101********1234
            int length = value.length();
            if (length == 18) {
                return value.substring(0, 6) + "********" + value.substring(14);
            } else if (length > 10) {
                return value.substring(0, 6) + "********" + value.substring(length - 4);
            }
        } else if (lowerField.contains("credit_card") || lowerField.contains("card")) {
            // 信用卡号脱敏：**** **** **** 1234
            int length = value.length();
            if (length >= 16) {
                return "**** **** **** " + value.substring(length - 4);
            }
        } else if (lowerField.contains("bank_account") || lowerField.contains("account_number")) {
            // 银行账号脱敏：**** **** **** 1234
            int length = value.length();
            if (length > 6) {
                return "**** **** **** " + value.substring(length - 4);
            }
        } else if (lowerField.contains("salary") || lowerField.contains("income")) {
            // 薪资脱敏：显示范围
            try {
                double amount = Double.parseDouble(value);
                if (amount < 10000) {
                    return "< 10000";
                } else if (amount < 50000) {
                    return "10000-50000";
                } else if (amount < 100000) {
                    return "50000-100000";
                } else {
                    return "> 100000";
                }
            } catch (NumberFormatException e) {
                // 如果不是数字，使用默认脱敏
            }
        } else if (lowerField.contains("address")) {
            // 地址脱敏：保留省份和城市
            int firstSpaceIndex = value.indexOf(' ');
            if (firstSpaceIndex > 0) {
                int secondSpaceIndex = value.indexOf(' ', firstSpaceIndex + 1);
                if (secondSpaceIndex > 0) {
                    return value.substring(0, secondSpaceIndex) + " ******";
                } else {
                    return value.substring(0, firstSpaceIndex) + " ******";
                }
            }
        } else if (lowerField.contains("birth_date") || lowerField.contains("date_of_birth")) {
            // 生日脱敏：只显示年份
            int yearSeparatorIndex = value.indexOf('-');
            if (yearSeparatorIndex < 0) {
                yearSeparatorIndex = value.indexOf('/');
            }
            if (yearSeparatorIndex > 0) {
                return value.substring(0, 4) + "-**-**";
            }
        }

        // 默认脱敏：保留前3后2
        int length = value.length();
        if (length <= 5) {
            return "******";
        }
        return value.substring(0, 3) + "******" + value.substring(length - 2);
    }

    private Object formatValue(Object value, String fieldName, Integer maxDecimalPlaces) {
        if (value == null) {
            return null;
        }

        if (maxDecimalPlaces == null) {
            maxDecimalPlaces = DEFAULT_MAX_DECIMAL_PLACES;
        }

        // 数值类型格式化
        if (value instanceof Number) {
            if (value instanceof BigDecimal) {
                return ((BigDecimal) value).setScale(maxDecimalPlaces, RoundingMode.HALF_UP);
            } else if (value instanceof Double || value instanceof Float) {
                return BigDecimal.valueOf(((Number) value).doubleValue())
                        .setScale(maxDecimalPlaces, RoundingMode.HALF_UP);
            } else {
                // 整数类型，保持原样
                return value;
            }
        }

        // 日期时间类型格式化
        if (value instanceof Timestamp) {
            LocalDateTime datetime = ((Timestamp) value).toLocalDateTime();
            return datetime.format(DATETIME_FORMATTER);
        } else if (value instanceof java.sql.Date) {
            LocalDate date = ((java.sql.Date) value).toLocalDate();
            return date.format(DATE_FORMATTER);
        } else if (value instanceof java.util.Date) {
            return new Timestamp(((java.util.Date) value).getTime())
                    .toLocalDateTime().format(DATETIME_FORMATTER);
        } else if (value instanceof LocalDateTime) {
            return ((LocalDateTime) value).format(DATETIME_FORMATTER);
        } else if (value instanceof LocalDate) {
            return ((LocalDate) value).format(DATE_FORMATTER);
        }

        // 字符串类型处理
        if (value instanceof String) {
            String strValue = ((String) value).trim();
            // 移除多余空格
            return strValue;
        }

        // 其他类型保持原样
        return value;
    }

    private List<Map<String, Object>> sortResult(List<Map<String, Object>> result, String sortBy, String sortOrder) {
        if (result == null) {
            throw new BusinessException("排序失败：结果数据为空");
        }
        if (sortBy == null || sortBy.isEmpty()) {
            throw new BusinessException("排序失败：排序字段不能为空");
        }
        
        boolean ascending = sortOrder == null || !sortOrder.equalsIgnoreCase("desc");

        result.sort((o1, o2) -> {
            Object v1 = o1.get(sortBy);
            Object v2 = o2.get(sortBy);

            if (v1 == null && v2 == null) {
                return 0;
            } else if (v1 == null) {
                return ascending ? -1 : 1;
            } else if (v2 == null) {
                return ascending ? 1 : -1;
            }

            int comparison;
            try {
                if (v1 instanceof Comparable && v1.getClass().isAssignableFrom(v2.getClass())) {
                    comparison = ((Comparable) v1).compareTo(v2);
                } else {
                    // 尝试转换为字符串比较
                    comparison = v1.toString().compareTo(v2.toString());
                }
            } catch (Exception e) {
                throw new BusinessException("排序失败：无法比较字段 '" + sortBy + "' 的值");
            }

            return ascending ? comparison : -comparison;
        });

        return result;
    }

    private List<Map<String, Object>> paginateResult(List<Map<String, Object>> result, int page, int pageSize) {
        if (result == null) {
            throw new BusinessException("分页失败：结果数据为空");
        }
        if (page < 1) {
            throw new BusinessException("分页失败：页码必须大于等于1");
        }
        if (pageSize < 1) {
            throw new BusinessException("分页失败：每页数量必须大于等于1");
        }
        
        int startIndex = (page - 1) * pageSize;
        int endIndex = Math.min(startIndex + pageSize, result.size());

        if (startIndex >= result.size()) {
            return Collections.emptyList();
        }

        try {
            return result.subList(startIndex, endIndex);
        } catch (Exception e) {
            throw new BusinessException("分页失败：无法获取指定范围的数据");
        }
    }

    /**
     * 计算结果统计信息
     * @param result 原始查询结果
     * @return 统计信息
     */
    public Map<String, Object> calculateStatistics(List<Map<String, Object>> result) {
        Map<String, Object> stats = createHashMap(5); // 预先分配容量

        if (result == null || result.isEmpty()) {
            stats.put("totalRows", 0);
            stats.put("columnCount", 0);
            return stats;
        }

        long totalRows = result.size();
        stats.put("totalRows", totalRows);
        stats.put("columnCount", result.get(0).size());
        
        // 开始时间，用于计算处理耗时
        long startTime = System.currentTimeMillis();
        
        // 计算详细的字段统计信息
        Map<String, Map<String, Object>> fieldStats = new HashMap<>();
        Map<String, Long> nullCountMap = new HashMap<>();
        Map<String, Map<String, Long>> typeCountMap = new HashMap<>();
        
        // 一次遍历计算所有统计信息
        for (Map<String, Object> row : result) {
            for (Map.Entry<String, Object> entry : row.entrySet()) {
                String fieldName = entry.getKey();
                Object value = entry.getValue();
                
                // 空值统计
                updateNullCount(nullCountMap, fieldName, value);
                
                // 数据类型统计
                updateDataTypeCount(typeCountMap, fieldName, value);
                
                // 字段统计信息
                updateFieldStatistics(fieldStats, fieldName, value);
            }
        }

        // 计算每个字段的空值百分比
        calculateNullPercentage(fieldStats, nullCountMap, totalRows);
        
        // 计算数值字段的平均值和字符串字段的平均长度
        calculateAverages(fieldStats, nullCountMap, totalRows);
        
        // 整合所有统计信息
        Map<String, Object> detailedStats = createHashMap(4);
        detailedStats.put("fieldStatistics", fieldStats);
        detailedStats.put("dataTypes", typeCountMap);
        detailedStats.put("nullValues", nullCountMap);
        
        // 添加处理耗时信息
        long processingTime = System.currentTimeMillis() - startTime;
        detailedStats.put("processingTimeMs", processingTime);
        
        stats.put("detailedStatistics", detailedStats);

        return stats;
    }
    
    /**
     * 创建指定初始容量的HashMap
     */
    private <K, V> HashMap<K, V> createHashMap(int initialCapacity) {
        return new HashMap<>(initialCapacity);
    }
    
    /**
     * 更新空值统计信息
     */
    private void updateNullCount(Map<String, Long> nullCountMap, String fieldName, Object value) {
        if (value == null) {
            nullCountMap.put(fieldName, nullCountMap.getOrDefault(fieldName, 0L) + 1);
        }
    }
    
    /**
     * 更新数据类型统计信息
     */
    private void updateDataTypeCount(Map<String, Map<String, Long>> typeCountMap, String fieldName, Object value) {
        String typeName = value != null ? value.getClass().getSimpleName() : "null";
        Map<String, Long> typeCount = typeCountMap.computeIfAbsent(fieldName, k -> new HashMap<>());
        typeCount.put(typeName, typeCount.getOrDefault(typeName, 0L) + 1);
    }
    
    /**
     * 更新字段统计信息
     */
    private void updateFieldStatistics(Map<String, Map<String, Object>> fieldStats, String fieldName, Object value) {
        if (value instanceof Number) {
            updateNumberStatistics(fieldStats, fieldName, (Number) value);
        } else if (value instanceof String) {
            updateStringStatistics(fieldStats, fieldName, (String) value);
        } else {
            updateOtherTypeStatistics(fieldStats, fieldName, value);
        }
    }
    
    /**
     * 更新数值类型统计信息
     */
    private void updateNumberStatistics(Map<String, Map<String, Object>> fieldStats, String fieldName, Number value) {
        double doubleValue = value.doubleValue();
        Map<String, Object> fieldStat = getFieldStats(fieldStats, fieldName);

        // 计算最小值、最大值、总和
        if (!fieldStat.containsKey("min")) {
            fieldStat.put("min", doubleValue);
            fieldStat.put("max", doubleValue);
            fieldStat.put("sum", doubleValue);
            fieldStat.put("count", 1L);
            fieldStat.put("type", "number");
        } else {
            // 直接更新统计值，避免重复获取
            fieldStat.put("min", Math.min((double) fieldStat.get("min"), doubleValue));
            fieldStat.put("max", Math.max((double) fieldStat.get("max"), doubleValue));
            fieldStat.put("sum", (double) fieldStat.get("sum") + doubleValue);
            fieldStat.put("count", (long) fieldStat.get("count") + 1);
        }
    }
    
    /**
     * 更新字符串类型统计信息
     */
    private void updateStringStatistics(Map<String, Map<String, Object>> fieldStats, String fieldName, String value) {
        Map<String, Object> fieldStat = getFieldStats(fieldStats, fieldName);
        fieldStat.put("type", "string");
        
        // 字符串长度统计
        int length = value.length();
        if (!fieldStat.containsKey("minLength")) {
            fieldStat.put("minLength", (long) length);
            fieldStat.put("maxLength", (long) length);
            fieldStat.put("totalLength", (long) length);
        } else {
            fieldStat.put("minLength", Math.min((long) fieldStat.get("minLength"), length));
            fieldStat.put("maxLength", Math.max((long) fieldStat.get("maxLength"), length));
            fieldStat.put("totalLength", (long) fieldStat.get("totalLength") + length);
        }
    }
    
    /**
     * 更新其他类型统计信息
     */
    private void updateOtherTypeStatistics(Map<String, Map<String, Object>> fieldStats, String fieldName, Object value) {
        Map<String, Object> fieldStat = getFieldStats(fieldStats, fieldName);
        fieldStat.put("type", value != null ? value.getClass().getSimpleName().toLowerCase() : "null");
    }
    
    /**
     * 获取或初始化字段统计信息
     */
    private Map<String, Object> getFieldStats(Map<String, Map<String, Object>> fieldStats, String fieldName) {
        return fieldStats.computeIfAbsent(fieldName, k -> createHashMap(10));
    }
    
    /**
     * 计算空值百分比
     */
    private void calculateNullPercentage(Map<String, Map<String, Object>> fieldStats, Map<String, Long> nullCountMap, long totalRows) {
        for (Map.Entry<String, Long> entry : nullCountMap.entrySet()) {
            String fieldName = entry.getKey();
            long nullCount = entry.getValue();
            double nullPercentage = (double) nullCount / totalRows * 100;
            Map<String, Object> fieldStat = getFieldStats(fieldStats, fieldName);
            fieldStat.put("nullCount", nullCount);
            fieldStat.put("nullPercentage", Math.round(nullPercentage * 100) / 100.0);
        }
    }
    
    /**
     * 计算平均值
     */
    private void calculateAverages(Map<String, Map<String, Object>> fieldStats, Map<String, Long> nullCountMap, long totalRows) {
        for (Map.Entry<String, Map<String, Object>> entry : fieldStats.entrySet()) {
            Map<String, Object> stat = entry.getValue();
            if ("number".equals(stat.get("type"))) {
                double sum = (double) stat.get("sum");
                long count = (long) stat.get("count");
                stat.put("avg", Math.round((sum / count) * 10000) / 10000.0); // 保留4位小数
            } else if ("string".equals(stat.get("type")) && stat.containsKey("totalLength")) {
                // 计算字符串字段的平均长度
                long totalLength = (long) stat.get("totalLength");
                long nonNullCount = totalRows - nullCountMap.getOrDefault(entry.getKey(), 0L);
                if (nonNullCount > 0) {
                    stat.put("avgLength", Math.round((double) totalLength / nonNullCount * 100) / 100.0);
                }
            }
        }
    }
}
package com.intelligent_data_analysis_system.utils;

import java.util.*;

public class AliasDictionary {

    // ✅ 只做“明确无歧义”的变体映射，避免误伤
    public static Map<String, String> tableAliases(String domain) {
        if ("HEALTHCARE".equalsIgnoreCase(domain)) {
            return Map.ofEntries(
                    Map.entry("patients", "patient_master_index"),
                    Map.entry("patient", "patient_master_index"),
                    Map.entry("visits", "medical_encounters"),
                    Map.entry("encounters", "medical_encounters"),
                    Map.entry("departments", "departments_wards"),
                    Map.entry("wards", "departments_wards"),
                    Map.entry("orders", "medical_orders"),
                    Map.entry("equipment_usage", "medical_equipment_usage"),
                    Map.entry("staff", "medical_staff"),
                    Map.entry("pharmacy", "pharmacy_inventory")
            );
        }
        // FINANCE
        return Map.ofEntries(
                Map.entry("trades", "transactions"),
                Map.entry("trade", "transactions"),
                Map.entry("portfolio", "portfolios"),
                Map.entry("counterparty", "counterparties"),
                Map.entry("client", "clients"),
                Map.entry("product", "products")
        );
    }

    // （可选）字段别名：先只做你截图里最常见的
    public static Map<String, String> columnAliases(String domain) {
        if ("HEALTHCARE".equalsIgnoreCase(domain)) {
            return Map.ofEntries(
                    Map.entry("name", "patient_name"),
                    Map.entry("phone", "contact_phone")
            );
        }
        return Map.ofEntries(
                Map.entry("id", "client_id")
        );
    }
}

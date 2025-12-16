package com.intelligent_data_analysis_system.domain.healthcare.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 医疗设备使用表（medical_equipment_usage）
 * 医疗设备使用记录和状态监控，包含设备基本信息、使用记录、维护历史等
 */
@Data
@TableName("medical_equipment_usage")
public class MedicalEquipmentUsage {

    /** 使用记录ID - 主键，使用记录唯一标识 */
    @TableId(value = "usage_id", type = IdType.AUTO)
    private Long usageId;

    /** 设备ID - 关联设备主表 */
    @TableField("equipment_id")
    private Long equipmentId;

    /** 就诊ID - 关联就诊记录表 */
    @TableField("encounter_id")
    private Long encounterId;

    /** 患者ID - 关联患者主索引表 */
    @TableField("patient_id")
    private Long patientId;

    /** 开始时间 - 设备使用开始时间 */
    @TableField("start_time")
    private LocalDateTime startTime;

    /** 结束时间 - 设备使用结束时间 */
    @TableField("end_time")
    private LocalDateTime endTime;

    /** 使用时长 - 使用时长（分钟） */
    @TableField("duration_minutes")
    private BigDecimal durationMinutes;

    /** 操作员ID - 设备操作人员ID */
    @TableField("operator_id")
    private Long operatorId;

    /** 科室ID - 使用科室 */
    @TableField("department_id")
    private Long departmentId;

    /** 使用类型 - 诊疗使用/维护检测/教学演示/科研使用 */
    @TableField("usage_type")
    private String usageType;

    /** 设备参数 - JSON格式设备参数 */
    @TableField("parameters_json")
    private String parametersJson;

    /** 读数记录 - JSON格式设备读数记录 */
    @TableField("readings_json")
    private String readingsJson;

    /** 能耗 - 设备能耗 */
    @TableField("energy_consumption")
    private String energyConsumption;

    /** 每分钟成本 - 设备每分钟使用成本 */
    @TableField("cost_per_minute")
    private String costPerMinute;

    /** 总费用 - 设备使用总费用 */
    @TableField("total_cost")
    private String totalCost;

    /** 校准日期 - 设备校准日期 */
    @TableField("calibration_date")
    private BigDecimal calibrationDate;

    /** 下次维护日期 - 下次计划维护日期 */
    @TableField("next_maintenance_date")
    private LocalDate nextMaintenanceDate;

    /** 错误代码 - 设备错误代码 */
    @TableField("error_codes")
    private String errorCodes;

    /** 使用状态 - 正常完成/异常终止/计划内维护 */
    @TableField("usage_status")
    private Integer usageStatus;

    /** 质量标志 - 使用质量是否合格 */
    @TableField("quality_flag")
    private Boolean qualityFlag;

    /** 审计意见 - 审计意见和备注 */
    @TableField("audit_comments")
    private String auditComments;

    /** 创建时间 - 记录创建时间 */
    @TableField("created_time")
    private LocalDateTime createdTime;

}

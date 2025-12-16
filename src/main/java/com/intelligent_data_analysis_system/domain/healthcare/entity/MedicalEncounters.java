package com.intelligent_data_analysis_system.domain.healthcare.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 就诊记录表（medical_encounters）
 * 患者就诊主记录表，每次就诊生成一条记录，与医嘱表、检查表、处方表等形成一对多关系
 */
@Data
@TableName("medical_encounters")
public class MedicalEncounters {

    /** 就诊ID - 主键，就诊唯一标识 */
    @TableId(value = "encounter_id", type = IdType.AUTO)
    private Long encounterId;

    /** 患者ID - 关联患者主索引表 */
    @TableField("patient_id")
    private Long patientId;

    /** 医院ID - 医院标识 */
    @TableField("hospital_id")
    private Long hospitalId;

    /** 科室ID - 就诊科室 */
    @TableField("department_id")
    private Long departmentId;

    /** 医生ID - 主治医生 */
    @TableField("doctor_id")
    private Long doctorId;

    /** 就诊类型 - 门诊/急诊/住院/体检/复诊 */
    @TableField("encounter_type")
    private String encounterType;

    /** 就诊时间 - 就诊开始时间 */
    @TableField("encounter_date")
    private LocalDate encounterDate;

    /** 出院时间 - 出院时间（住院患者） */
    @TableField("discharge_date")
    private LocalDate dischargeDate;

    /** 主诉 - 患者主诉症状 */
    @TableField("chief_complaint")
    private String chiefComplaint;

    /** 诊断代码 - 疾病诊断代码 */
    @TableField("diagnosis_code")
    private String diagnosisCode;

    /** 诊断描述 - 疾病诊断描述 */
    @TableField("diagnosis_desc")
    private String diagnosisDesc;

    /** 严重程度 - 轻/中/重/危重 */
    @TableField("severity_level")
    private String severityLevel;

    /** 体温 - 患者体温 */
    @TableField("temperature")
    private String temperature;

    /** 血压 - 血压测量值 */
    @TableField("blood_pressure")
    private String bloodPressure;

    /** 心率 - 心率 */
    @TableField("heart_rate")
    private String heartRate;

    /** 入院类型 - 平诊/急诊/转院 */
    @TableField("admission_type")
    private String admissionType;

    /** 出院去向 - 治愈出院/好转出院等 */
    @TableField("discharge_disposition")
    private String dischargeDisposition;

    /** 总费用 - 就诊总费用 */
    @TableField("total_cost")
    private String totalCost;

    /** 医保支付 - 医保支付金额 */
    @TableField("insurance_payment")
    private String insurancePayment;

    /** 患者支付 - 患者自付金额 */
    @TableField("patient_payment")
    private String patientPayment;

    /** 是否支付 - 费用是否已支付 */
    @TableField("is_paid")
    private Boolean isPaid;

    /** 就诊状态 - 已完成/进行中/已取消 */
    @TableField("encounter_status")
    private Integer encounterStatus;

    /** 创建人 - 记录创建人ID */
    @TableField("created_by")
    private String createdBy;

    /** 创建时间 - 记录创建时间 */
    @TableField("created_time")
    private LocalDateTime createdTime;

    /** 更新时间 - 记录更新时间 */
    @TableField("updated_time")
    private LocalDateTime updatedTime;

}

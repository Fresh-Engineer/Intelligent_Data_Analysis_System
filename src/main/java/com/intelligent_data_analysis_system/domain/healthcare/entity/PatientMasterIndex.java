package com.intelligent_data_analysis_system.domain.healthcare.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 患者主索引表（patient_master_index）
 * 患者核心档案表，记录患者基本信息，与其他所有患者相关表通过patient_id关联
 */
@Data
@TableName("patient_master_index")
public class PatientMasterIndex {

    /** 患者ID - 主键，患者唯一标识 */
    @TableId(value = "patient_id", type = IdType.AUTO)
    private Long patientId;

    /** 身份证号 - 患者身份证号码，唯一标识 */
    @TableField("national_id")
    private Long nationalId;

    /** 就诊卡号 - 医院就诊卡号 */
    @TableField("medical_card_no")
    private Integer medicalCardNo;

    /** 患者姓名 - 患者姓名 */
    @TableField("patient_name")
    private String patientName;

    /** 性别 - M-男, F-女, U-未知 */
    @TableField("gender")
    private String gender;

    /** 出生日期 - 患者出生日期 */
    @TableField("birth_date")
    private LocalDate birthDate;

    /** 年龄 - 患者年龄 */
    @TableField("age")
    private String age;

    /** 血型 - A/B/AB/O */
    @TableField("blood_type")
    private String bloodType;

    /** 婚姻状况 - 婚姻状态 */
    @TableField("marital_status")
    private Integer maritalStatus;

    /** 联系电话 - 患者联系电话 */
    @TableField("contact_phone")
    private String contactPhone;

    /** 紧急联系人 - JSON格式紧急联系人信息 */
    @TableField("emergency_contact")
    private String emergencyContact;

    /** 地址信息 - JSON格式地址信息 */
    @TableField("address_json")
    private String addressJson;

    /** 医保类型 - 医保类型：城镇职工/城乡居民等 */
    @TableField("insurance_type")
    private String insuranceType;

    /** 医保号 - 医保卡号 */
    @TableField("insurance_no")
    private Integer insuranceNo;

    /** 医保余额 - 医保账户余额 */
    @TableField("insurance_balance")
    private String insuranceBalance;

    /** 是否黑名单 - 是否在黑名单中 */
    @TableField("is_blacklist")
    private Boolean isBlacklist;

    /** 患者等级 - 患者等级：普通/VIP/SVIP */
    @TableField("patient_level")
    private String patientLevel;

    /** 创建时间 - 记录创建时间 */
    @TableField("create_time")
    private LocalDateTime createTime;

    /** 更新时间 - 记录更新时间 */
    @TableField("update_time")
    private LocalDateTime updateTime;

    /** 数据来源 - 数据来源：门诊/住院/体检等 */
    @TableField("data_source")
    private String dataSource;

    /** 备注 - 备注信息 */
    @TableField("remark")
    private String remark;

    /** 删除标志 - N-正常, Y-已删除 */
    @TableField("delete_flag")
    private Boolean deleteFlag;

}

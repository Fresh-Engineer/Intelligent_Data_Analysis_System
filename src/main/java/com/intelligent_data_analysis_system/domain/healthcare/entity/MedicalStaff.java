package com.intelligent_data_analysis_system.domain.healthcare.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 医护人员表（medical_staff）
 * 医院员工信息，包含医生、护士、技术人员等，支持自关联(上下级关系)
 */
@Data
@TableName("medical_staff")
public class MedicalStaff {

    /** 员工ID - 主键，员工唯一标识 */
    @TableId(value = "staff_id", type = IdType.AUTO)
    private Long staffId;

    /** 工号 - 员工工号 */
    @TableField("employee_no")
    private Integer employeeNo;

    /** 员工姓名 - 员工姓名 */
    @TableField("staff_name")
    private String staffName;

    /** 性别 - M-男, F-女 */
    @TableField("gender")
    private String gender;

    /** 出生日期 - 员工出生日期 */
    @TableField("birth_date")
    private LocalDate birthDate;

    /** 入职日期 - 员工入职日期 */
    @TableField("hire_date")
    private LocalDate hireDate;

    /** 科室ID - 所属科室 */
    @TableField("department_id")
    private Long departmentId;

    /** 职位 - 主任医师/副主任医师/护士等 */
    @TableField("job_title")
    private String jobTitle;

    /** 资质等级 - 初级/中级/副高级/高级 */
    @TableField("qualification_level")
    private String qualificationLevel;

    /** 专业方向 - 内科/外科/妇产科/儿科等 */
    @TableField("specialization")
    private String specialization;

    /** 上级ID - 上级领导ID（自关联） */
    @TableField("supervisor_id")
    private Long supervisorId;

    /** 联系电话 - 员工联系电话 */
    @TableField("contact_phone")
    private String contactPhone;

    /** 邮箱 - 员工邮箱 */
    @TableField("email")
    private String email;

    /** 排班信息 - JSON格式排班信息 */
    @TableField("work_schedule")
    private String workSchedule;

    /** 年假余额 - 年假剩余天数 */
    @TableField("annual_leave_balance")
    private String annualLeaveBalance;

    /** 绩效分数 - 员工绩效评分 */
    @TableField("performance_score")
    private BigDecimal performanceScore;

    /** 证书信息 - JSON格式证书信息 */
    @TableField("certification_json")
    private String certificationJson;

    /** 是否在职 - 是否在职状态 */
    @TableField("is_active")
    private Boolean isActive;

    /** 离职日期 - 员工离职日期 */
    @TableField("resignation_date")
    private LocalDate resignationDate;

    /** 紧急联系人 - 紧急联系人信息 */
    @TableField("emergency_contact")
    private String emergencyContact;

    /** 住址 - 员工住址 */
    @TableField("address")
    private String address;

    /** 创建时间 - 记录创建时间 */
    @TableField("created_time")
    private LocalDateTime createdTime;

    /** 更新时间 - 记录更新时间 */
    @TableField("updated_time")
    private LocalDateTime updatedTime;

}

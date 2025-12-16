package com.intelligent_data_analysis_system.domain.finance.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 客户经理表（managers）
 * 存储客户经理基本信息，包含层级关系和管理绩效
 */
@Data
@TableName("managers")
public class Managers {

    /** 经理ID - 主键，客户经理唯一标识 */
    @TableId(value = "manager_id", type = IdType.AUTO)
    private Long managerId;

    /** 工号 - 唯一索引，经理工号编码 */
    @TableField("manager_code")
    private String managerCode;

    /** 姓名 - 客户经理姓名 */
    @TableField("manager_name")
    private String managerName;

    /** 部门ID - 部门标识，用于自关联 */
    @TableField("department_id")
    private Long departmentId;

    /** 部门名称 - 所属部门名称 */
    @TableField("department_name")
    private String departmentName;

    /** 职级 - 职位级别：助理、经理、高级经理、总监、总经理 */
    @TableField("position_level")
    private String positionLevel;

    /** 入职日期 - 入职时间 */
    @TableField("hire_date")
    private LocalDate hireDate;

    /** 管理资产总额 - 管理客户资产总和 */
    @TableField("manage_assets_total")
    private BigDecimal manageAssetsTotal;

    /** 客户数量 - 管理的客户数量 */
    @TableField("client_count")
    private Integer clientCount;

    /** 绩效评分 - 绩效评分（0-100） */
    @TableField("performance_score")
    private BigDecimal performanceScore;

    /** 上级ID - 上级经理ID，外键关联本表 */
    @TableField("superior_id")
    private Long superiorId;

}

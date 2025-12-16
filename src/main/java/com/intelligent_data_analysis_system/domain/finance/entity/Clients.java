package com.intelligent_data_analysis_system.domain.finance.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 客户表（clients）
 * 存储客户核心信息，支持多维度客户分析
 */
@Data
@TableName("clients")
public class Clients {

    /** 客户ID - 主键，客户唯一标识 */
    @TableId(value = "client_id", type = IdType.AUTO)
    private Long clientId;

    /** 客户编码 - 唯一索引，包含机构(C)和个人(P)前缀 */
    @TableField("client_code")
    private String clientCode;

    /** 客户名称 - 客户姓名或机构名称 */
    @TableField("client_name")
    private String clientName;

    /** 客户类型 - 个人、机构、家族信托、养老金 */
    @TableField("client_type")
    private String clientType;

    /** 风险等级 - 保守、稳健、平衡、成长、进取 */
    @TableField("risk_level")
    private String riskLevel;

    /** 注册日期 - 客户注册时间 */
    @TableField("register_date")
    private LocalDate registerDate;

    /** 总资产规模 - 客户总资产，可能包含空值和负值 */
    @TableField("total_assets")
    private BigDecimal totalAssets;

    /** 联系信息 - JSON格式，包含电话、邮箱、地址等 */
    @TableField("contact_info")
    private String contactInfo;

    /** 客户经理ID - 外键，关联客户经理表 */
    @TableField("manager_id")
    private Long managerId;

    /** 创建时间 - 记录创建时间戳 */
    @TableField("create_time")
    private LocalDateTime createTime;

    /** 更新时间 - 记录更新时间戳 */
    @TableField("update_time")
    private LocalDateTime updateTime;

    /** 状态 - 1正常、2冻结、3销户 */
    @TableField("status")
    private Integer status;

}

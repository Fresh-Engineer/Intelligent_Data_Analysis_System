package com.intelligent_data_analysis_system.domain.finance.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 对手方表（counterparties）
 * 存储交易对手方信息，支持信用风险评估
 */
@Data
@TableName("counterparties")
public class Counterparties {

    /** 对手方ID - 主键，对手方唯一标识 */
    @TableId(value = "counterparty_id", type = IdType.AUTO)
    private Long counterpartyId;

    /** 对手方编码 - 唯一索引，对手方编码 */
    @TableField("counterparty_code")
    private String counterpartyCode;

    /** 对手方名称 - 对手方完整名称 */
    @TableField("counterparty_name")
    private String counterpartyName;

    /** 对手方类型 - 券商、银行、基金公司、保险公司、其他机构 */
    @TableField("counterparty_type")
    private String counterpartyType;

    /** 信用评级 - AAA、AA+、AA、A+、A、BBB、BB、B、CCC、D */
    @TableField("credit_rating")
    private String creditRating;

    /** 国家代码 - 2位国家代码 */
    @TableField("country_code")
    private String countryCode;

    /** 是否有效 - 对手方是否有效状态 */
    @TableField("is_active")
    private Boolean isActive;

    /** 成立日期 - 对手方成立时间 */
    @TableField("establish_date")
    private LocalDate establishDate;

    /** 注册资本 - 注册资本金额 */
    @TableField("registered_capital")
    private BigDecimal registeredCapital;

}

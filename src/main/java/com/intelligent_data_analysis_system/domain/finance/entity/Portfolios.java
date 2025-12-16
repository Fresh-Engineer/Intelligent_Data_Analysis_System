package com.intelligent_data_analysis_system.domain.finance.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 投资组合表（portfolios）
 * 存储客户投资组合信息，支持组合绩效分析
 */
@Data
@TableName("portfolios")
public class Portfolios {

    /** 组合ID - 主键，投资组合唯一标识 */
    @TableId(value = "portfolio_id", type = IdType.AUTO)
    private Long portfolioId;

    /** 组合代码 - 唯一索引，组合编码 */
    @TableField("portfolio_code")
    private String portfolioCode;

    /** 客户ID - 外键，关联客户表 */
    @TableField("client_id")
    private Long clientId;

    /** 组合类型 - 全权委托、投资顾问、自助交易、智能投顾 */
    @TableField("portfolio_type")
    private String portfolioType;

    /** 目标收益率 - 组合投资目标收益率 */
    @TableField("target_return")
    private BigDecimal targetReturn;

    /** 最大回撤限制 - 最大允许回撤幅度 */
    @TableField("max_drawdown_limit")
    private BigDecimal maxDrawdownLimit;

    /** 组合成立日 - 组合成立时间 */
    @TableField("inception_date")
    private LocalDate inceptionDate;

    /** 组合终止日 - 组合终止时间，可能为空 */
    @TableField("termination_date")
    private LocalDate terminationDate;

    /** 当前市值 - 组合当前总市值 */
    @TableField("current_value")
    private BigDecimal currentValue;

    /** 累计投入金额 - 累计投入资金总额 */
    @TableField("contribution_amount")
    private BigDecimal contributionAmount;

    /** 风险调整后收益 - 夏普比率等风险调整后收益指标 */
    @TableField("risk_adjust_return")
    private BigDecimal riskAdjustReturn;

    /** 创建人 - 组合创建人 */
    @TableField("create_user")
    private String createUser;

    /** 创建时间 - 组合创建时间戳 */
    @TableField("create_time")
    private LocalDateTime createTime;

}

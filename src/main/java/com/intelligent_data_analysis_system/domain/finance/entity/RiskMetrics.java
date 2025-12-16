package com.intelligent_data_analysis_system.domain.finance.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 风险监控表（risk_metrics）
 * 存储每日风险指标，支持风险分析监控
 */
@Data
@TableName("risk_metrics")
public class RiskMetrics {

    /** 指标ID - 主键，风险指标唯一标识 */
    @TableId(value = "metric_id", type = IdType.AUTO)
    private Long metricId;

    /** 组合ID - 外键，关联投资组合表 */
    @TableField("portfolio_id")
    private Long portfolioId;

    /** 计算日期 - 风险指标计算日期 */
    @TableField("calc_date")
    private LocalDate calcDate;

    /** VaR值 - 95%置信度风险价值 */
    @TableField("var_95")
    private String var95;

    /** 预期损失 - 预期尾部损失 */
    @TableField("expected_shortfall")
    private String expectedShortfall;

    /** 最大回撤 - 历史最大回撤幅度 */
    @TableField("max_drawdown")
    private BigDecimal maxDrawdown;

    /** 波动率 - 收益波动率 */
    @TableField("volatility")
    private String volatility;

    /** 贝塔系数 - 市场风险系数 */
    @TableField("beta")
    private String beta;

    /** 夏普比率 - 风险调整后收益指标 */
    @TableField("sharp_ratio")
    private BigDecimal sharpRatio;

    /** 跟踪误差 - 与基准的跟踪误差 */
    @TableField("tracking_error")
    private String trackingError;

    /** 集中度 - 组合持仓集中度 */
    @TableField("concentration_ratio")
    private BigDecimal concentrationRatio;

    /** 流动性评分 - 流动性评分1-10分 */
    @TableField("liquidity_score")
    private BigDecimal liquidityScore;

    /** 风险敞口 - JSON格式，各类风险敞口比例 */
    @TableField("risk_exposure")
    private String riskExposure;

    /** 是否预警 - 是否触发风险预警 */
    @TableField("is_alert")
    private Boolean isAlert;

}

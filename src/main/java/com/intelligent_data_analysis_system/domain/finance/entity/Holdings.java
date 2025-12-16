package com.intelligent_data_analysis_system.domain.finance.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 持仓明细表（holdings）
 * 存储每日持仓快照，数据量大，支持窗口函数分析
 */
@Data
@TableName("holdings")
public class Holdings {

    /** 持仓记录ID - 主键，持仓记录唯一标识 */
    @TableId(value = "holding_id", type = IdType.AUTO)
    private Long holdingId;

    /** 组合ID - 外键，关联投资组合表 */
    @TableField("portfolio_id")
    private Long portfolioId;

    /** 产品ID - 外键，关联产品信息表 */
    @TableField("product_id")
    private Long productId;

    /** 交易日期 - 持仓快照日期 */
    @TableField("trade_date")
    private LocalDate tradeDate;

    /** 持有数量 - 持有产品数量，可能包含零值和负值 */
    @TableField("holding_quantity")
    private String holdingQuantity;

    /** 平均成本 - 持仓平均成本价 */
    @TableField("average_cost")
    private String averageCost;

    /** 市价 - 当前市场价格 */
    @TableField("market_price")
    private String marketPrice;

    /** 市值 - 持仓市值（数量*市价） */
    @TableField("market_value")
    private BigDecimal marketValue;

    /** 浮动盈亏 - 持仓浮动盈亏（市值-成本） */
    @TableField("unrealized_pnl")
    private String unrealizedPnl;

    /** 持有天数 - 持仓持有天数 */
    @TableField("holding_days")
    private String holdingDays;

    /** 是否质押 - 持仓是否被质押 */
    @TableField("is_pledged")
    private Boolean isPledged;

    /** 质押比例 - 质押比例，可能为空 */
    @TableField("pledge_ratio")
    private BigDecimal pledgeRatio;

    /** 最后更新时间 - 记录最后更新时间戳 */
    @TableField("last_update")
    private String lastUpdate;

}

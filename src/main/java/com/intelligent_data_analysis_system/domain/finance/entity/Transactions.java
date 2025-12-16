package com.intelligent_data_analysis_system.domain.finance.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 交易流水表（transactions）
 * 存储详细交易记录，数据量大，支持复杂交易分析
 */
@Data
@TableName("transactions")
public class Transactions {

    /** 交易流水号 - 主键，交易记录唯一标识 */
    @TableId(value = "transaction_id", type = IdType.AUTO)
    private Long transactionId;

    /** 交易所成交编号 - 唯一索引，交易所成交编号 */
    @TableField("trade_id")
    private Long tradeId;

    /** 组合ID - 外键，关联投资组合表 */
    @TableField("portfolio_id")
    private Long portfolioId;

    /** 产品ID - 外键，关联产品信息表 */
    @TableField("product_id")
    private Long productId;

    /** 交易类型 - 买入、卖出、分红、派息、申购、赎回、转换 */
    @TableField("transaction_type")
    private String transactionType;

    /** 交易日期 - 交易发生日期 */
    @TableField("trade_date")
    private LocalDate tradeDate;

    /** 结算日期 - 交易结算日期，用于日期差异计算 */
    @TableField("settlement_date")
    private LocalDate settlementDate;

    /** 交易数量 - 交易产品数量 */
    @TableField("transaction_quantity")
    private String transactionQuantity;

    /** 成交价格 - 交易成交单价 */
    @TableField("transaction_price")
    private String transactionPrice;

    /** 成交金额 - 交易总金额 */
    @TableField("transaction_amount")
    private BigDecimal transactionAmount;

    /** 佣金费用 - 交易佣金费用 */
    @TableField("commission_fee")
    private BigDecimal commissionFee;

    /** 印花税 - 交易印花税 */
    @TableField("stamp_duty")
    private String stampDuty;

    /** 净额 - 交易净额（金额-费用-税费） */
    @TableField("net_amount")
    private BigDecimal netAmount;

    /** 对手方ID - 外键，关联对手方表 */
    @TableField("counterparty_id")
    private Long counterpartyId;

    /** 交易员ID - 交易员标识 */
    @TableField("trader_id")
    private Long traderId;

    /** 交易时间 - 交易时间戳（微秒精度） */
    @TableField("trade_time")
    private LocalDateTime tradeTime;

    /** 交易状态 - 已报、已成、已撤、部成、部撤 */
    @TableField("status")
    private Integer status;

}

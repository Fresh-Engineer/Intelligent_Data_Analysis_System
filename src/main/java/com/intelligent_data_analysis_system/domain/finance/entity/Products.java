package com.intelligent_data_analysis_system.domain.finance.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 产品信息表（products）
 * 存储金融产品信息，支持多维度产品分析
 */
@Data
@TableName("products")
public class Products {

    /** 产品ID - 主键，产品唯一标识 */
    @TableId(value = "product_id", type = IdType.AUTO)
    private Long productId;

    /** 产品代码 - 唯一索引，产品编码 */
    @TableField("product_code")
    private String productCode;

    /** 产品名称 - 产品完整名称 */
    @TableField("product_name")
    private String productName;

    /** 产品类型 - 股票型、债券型、混合型、货币型、QDII、另类投资 */
    @TableField("product_type")
    private String productType;

    /** 风险评级 - 1-5级，1最低风险，5最高风险 */
    @TableField("risk_rating")
    private String riskRating;

    /** 币种 - 产品计价币种：USD、CNY、HKD等 */
    @TableField("currency")
    private String currency;

    /** 管理费率 - 年化管理费率，用于精度计算考核 */
    @TableField("management_fee")
    private BigDecimal managementFee;

    /** 业绩报酬比例 - 业绩提成比例 */
    @TableField("performance_fee_rate")
    private BigDecimal performanceFeeRate;

    /** 成立日期 - 产品成立时间 */
    @TableField("inception_date")
    private LocalDate inceptionDate;

    /** 业绩比较基准 - 业绩比较基准指数 */
    @TableField("benchmark_index")
    private String benchmarkIndex;

    /** 资产配置比例 - JSON格式，股票、债券、现金配置比例 */
    @TableField("asset_allocation")
    private String assetAllocation;

    /** 是否有效产品 - 产品是否有效状态 */
    @TableField("is_active")
    private Boolean isActive;

}

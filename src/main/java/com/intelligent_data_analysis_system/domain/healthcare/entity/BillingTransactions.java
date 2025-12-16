package com.intelligent_data_analysis_system.domain.healthcare.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 费用明细与结算表（billing_transactions）
 * 费用明细和结算记录，与就诊、药品、设备等多表关联，支持复杂的费用分摊逻辑
 */
@Data
@TableName("billing_transactions")
public class BillingTransactions {

    /** 交易ID - 主键，交易记录唯一标识 */
    @TableId(value = "transaction_id", type = IdType.AUTO)
    private Long transactionId;

    /** 账单号 - 账单编号 */
    @TableField("billing_no")
    private Integer billingNo;

    /** 就诊ID - 关联就诊记录表 */
    @TableField("encounter_id")
    private Long encounterId;

    /** 患者ID - 关联患者主索引表 */
    @TableField("patient_id")
    private Long patientId;

    /** 交易日期 - 交易发生时间 */
    @TableField("transaction_date")
    private LocalDate transactionDate;

    /** 交易类型 - 药品费/检查费/检验费/治疗费等 */
    @TableField("transaction_type")
    private String transactionType;

    /** 项目ID - 收费项目ID */
    @TableField("item_id")
    private Long itemId;

    /** 项目类型 - 药品/检查/治疗/手术/材料 */
    @TableField("item_type")
    private String itemType;

    /** 项目描述 - 收费项目描述 */
    @TableField("item_description")
    private String itemDescription;

    /** 数量 - 收费数量 */
    @TableField("quantity")
    private String quantity;

    /** 单价 - 项目单价 */
    @TableField("unit_price")
    private String unitPrice;

    /** 折扣率 - 折扣比例 */
    @TableField("discount_rate")
    private String discountRate;

    /** 折扣金额 - 折扣金额 */
    @TableField("discount_amount")
    private BigDecimal discountAmount;

    /** 应税金额 - 应税金额 */
    @TableField("taxable_amount")
    private BigDecimal taxableAmount;

    /** 税率 - 税率 */
    @TableField("tax_rate")
    private String taxRate;

    /** 税额 - 税额 */
    @TableField("tax_amount")
    private BigDecimal taxAmount;

    /** 净额 - 税后净额 */
    @TableField("net_amount")
    private BigDecimal netAmount;

    /** 医保覆盖率 - 医保覆盖比例 */
    @TableField("insurance_coverage")
    private String insuranceCoverage;

    /** 医保支付 - 医保支付金额 */
    @TableField("insurance_paid")
    private String insurancePaid;

    /** 患者支付 - 患者自付金额 */
    @TableField("patient_paid")
    private String patientPaid;

    /** 未付金额 - 未支付金额 */
    @TableField("outstanding_amount")
    private BigDecimal outstandingAmount;

    /** 支付方式 - 现金/银行卡/微信/支付宝等 */
    @TableField("payment_method")
    private String paymentMethod;

    /** 支付状态 - 已支付/未支付/部分支付等 */
    @TableField("payment_status")
    private Integer paymentStatus;

    /** 发票号 - 发票号码 */
    @TableField("invoice_no")
    private Integer invoiceNo;

    /** 成本中心代码 - 成本中心编码 */
    @TableField("cost_center_code")
    private String costCenterCode;

    /** 科室ID - 收费科室 */
    @TableField("department_id")
    private Long departmentId;

    /** 冲销参考ID - 冲销交易的参考ID（自关联） */
    @TableField("reversal_ref_id")
    private Long reversalRefId;

    /** 审计跟踪 - JSON格式审计跟踪信息 */
    @TableField("audit_trail")
    private String auditTrail;

    /** 创建人 - 记录创建人ID */
    @TableField("created_by")
    private String createdBy;

    /** 创建时间 - 记录创建时间 */
    @TableField("created_time")
    private LocalDateTime createdTime;

}

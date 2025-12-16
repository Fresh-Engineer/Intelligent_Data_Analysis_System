package com.intelligent_data_analysis_system.domain.healthcare.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 药品库存与采购表（pharmacy_inventory）
 * 药品库存管理，包含采购、入库、出库、库存等信息，与医嘱表、供应商表等多表关联
 */
@Data
@TableName("pharmacy_inventory")
public class PharmacyInventory {

    /** 库存ID - 主键，库存记录唯一标识 */
    @TableId(value = "inventory_id", type = IdType.AUTO)
    private Long inventoryId;

    /** 药品ID - 关联药品目录 */
    @TableField("drug_id")
    private Long drugId;

    /** 批号 - 药品批号 */
    @TableField("batch_number")
    private String batchNumber;

    /** 供应商ID - 关联供应商表 */
    @TableField("supplier_id")
    private Long supplierId;

    /** 采购订单号 - 采购订单编号 */
    @TableField("purchase_order_no")
    private Integer purchaseOrderNo;

    /** 采购日期 - 药品采购日期 */
    @TableField("purchase_date")
    private LocalDate purchaseDate;

    /** 有效期 - 药品有效期 */
    @TableField("expiration_date")
    private BigDecimal expirationDate;

    /** 存储位置 - 药品存储位置 */
    @TableField("storage_location")
    private String storageLocation;

    /** 当前数量 - 当前库存数量 */
    @TableField("current_quantity")
    private String currentQuantity;

    /** 计量单位 - 盒/瓶/支/袋/板 */
    @TableField("unit_of_measure")
    private String unitOfMeasure;

    /** 单位成本 - 药品单位成本 */
    @TableField("unit_cost")
    private String unitCost;

    /** 总成本 - 药品总成本 */
    @TableField("total_cost")
    private String totalCost;

    /** 再订货点 - 再订货库存水平 */
    @TableField("reorder_level")
    private String reorderLevel;

    /** 安全库存 - 安全库存数量 */
    @TableField("safety_stock")
    private String safetyStock;

    /** 最后补货日期 - 最后一次补货日期 */
    @TableField("last_restock_date")
    private LocalDate lastRestockDate;

    /** 最后出库日期 - 最后一次出库日期 */
    @TableField("last_issue_date")
    private LocalDate lastIssueDate;

    /** 库存状态 - 在库/待验/停用/退货/报损 */
    @TableField("inventory_status")
    private Integer inventoryStatus;

    /** 质检结果 - 合格/不合格/待检 */
    @TableField("quality_check_result")
    private String qualityCheckResult;

    /** 温度要求 - 常温/阴凉/冷藏/冷冻 */
    @TableField("temperature_requirement")
    private String temperatureRequirement;

    /** 是否管控药品 - 是否为管控药品 */
    @TableField("is_controlled_substance")
    private Boolean isControlledSubstance;

    /** 保质期天数 - 药品保质期天数 */
    @TableField("shelf_life_days")
    private String shelfLifeDays;

    /** 备注 - 备注信息 */
    @TableField("remark")
    private String remark;

    /** 创建人 - 记录创建人ID */
    @TableField("created_by")
    private String createdBy;

    /** 创建时间 - 记录创建时间 */
    @TableField("created_time")
    private LocalDateTime createdTime;

}

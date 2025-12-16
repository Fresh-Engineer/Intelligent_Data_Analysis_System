package com.intelligent_data_analysis_system.domain.healthcare.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 医嘱执行表（medical_orders）
 * 医嘱详细执行记录，与就诊记录多对一，与药品库存、检查项目等多表关联
 */
@Data
@TableName("medical_orders")
public class MedicalOrders {

    /** 医嘱ID - 主键，医嘱唯一标识 */
    @TableId(value = "order_id", type = IdType.AUTO)
    private Long orderId;

    /** 就诊ID - 关联就诊记录表 */
    @TableField("encounter_id")
    private Long encounterId;

    /** 医嘱类型 - 药品/检查/检验/治疗/手术/护理 */
    @TableField("order_type")
    private String orderType;

    /** 项目ID - 药品/检查项目ID */
    @TableField("item_id")
    private Long itemId;

    /** 项目类型 - 项目类型标识 */
    @TableField("item_type")
    private String itemType;

    /** 项目名称 - 药品或项目名称 */
    @TableField("item_name")
    private String itemName;

    /** 医嘱数量 - 医嘱数量 */
    @TableField("order_quantity")
    private String orderQuantity;

    /** 医嘱单位 - 片/支/瓶/次/项/小时 */
    @TableField("order_unit")
    private String orderUnit;

    /** 给药频率 - 每日一次/每日两次/必要时等 */
    @TableField("frequency")
    private String frequency;

    /** 给药途径 - 口服/静脉注射/肌肉注射等 */
    @TableField("administration_route")
    private BigDecimal administrationRoute;

    /** 开始时间 - 医嘱开始执行时间 */
    @TableField("start_datetime")
    private LocalDateTime startDatetime;

    /** 结束时间 - 医嘱结束执行时间 */
    @TableField("end_datetime")
    private LocalDateTime endDatetime;

    /** 执行护士ID - 执行护士ID */
    @TableField("executing_nurse_id")
    private Long executingNurseId;

    /** 执行时间 - 实际执行时间 */
    @TableField("executed_datetime")
    private LocalDateTime executedDatetime;

    /** 执行状态 - 已执行/执行中/已取消/未执行 */
    @TableField("execution_status")
    private Integer executionStatus;

    /** 取消原因 - 医嘱取消原因 */
    @TableField("cancel_reason")
    private String cancelReason;

    /** 单价 - 项目单价 */
    @TableField("unit_price")
    private String unitPrice;

    /** 总价 - 项目总价 */
    @TableField("total_price")
    private String totalPrice;

    /** 是否紧急 - 是否为紧急医嘱 */
    @TableField("is_urgent")
    private Boolean isUrgent;

    /** 医嘱优先级 - 优先级1-5 */
    @TableField("order_priority")
    private String orderPriority;

    /** 备注 - 备注信息 */
    @TableField("remark")
    private String remark;

    /** 审计跟踪 - JSON格式审计跟踪信息 */
    @TableField("audit_trail")
    private String auditTrail;

    /** 创建时间 - 记录创建时间 */
    @TableField("created_time")
    private LocalDateTime createdTime;

}

package com.intelligent_data_analysis_system.domain.healthcare.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDate;

/**
 * 科室与病区表（departments_wards）
 * 医院科室和病区配置表，包含层级关系和资源配置
 */
@Data
@TableName("departments_wards")
public class DepartmentsWards {

    /** 科室病区ID - 主键，科室病区唯一标识 */
    @TableId(value = "dept_ward_id", type = IdType.AUTO)
    private Long deptWardId;

    /** 科室代码 - 科室编码 */
    @TableField("code")
    private String code;

    /** 科室名称 - 科室名称 */
    @TableField("name")
    private String name;

    /** 类型 - 临床科室/医技科室/病区 */
    @TableField("type")
    private String type;

    /** 上级ID - 上级科室ID（自关联） */
    @TableField("parent_id")
    private Long parentId;

    /** 医院ID - 所属医院 */
    @TableField("hospital_id")
    private Long hospitalId;

    /** 所在楼栋 - 所在楼栋名称 */
    @TableField("location_building")
    private String locationBuilding;

    /** 所在楼层 - 所在楼层 */
    @TableField("location_floor")
    private String locationFloor;

    /** 房间号 - 科室房间号 */
    @TableField("location_room")
    private String locationRoom;

    /** 总床位数 - 总床位数 */
    @TableField("total_beds")
    private String totalBeds;

    /** 可用床位 - 可用床位数 */
    @TableField("available_beds")
    private String availableBeds;

    /** 科室主任ID - 科室主任ID */
    @TableField("head_doctor_id")
    private Long headDoctorId;

    /** 护士长ID - 护士长ID */
    @TableField("head_nurse_id")
    private Long headNurseId;

    /** 专业重点 - 科室专业重点方向 */
    @TableField("specialty_focus")
    private String specialtyFocus;

    /** 设备列表 - JSON格式设备列表 */
    @TableField("equipment_list")
    private String equipmentList;

    /** 成本中心代码 - 成本中心编码 */
    @TableField("cost_center_code")
    private String costCenterCode;

    /** 收入目标 - 科室收入目标 */
    @TableField("revenue_target")
    private String revenueTarget;

    /** 月度预算 - 月度预算金额 */
    @TableField("monthly_budget")
    private String monthlyBudget;

    /** 联系电话 - 科室联系电话 */
    @TableField("contact_number")
    private String contactNumber;

    /** 邮箱 - 科室邮箱 */
    @TableField("email")
    private String email;

    /** 是否启用 - 是否启用状态 */
    @TableField("is_active")
    private Boolean isActive;

    /** 创建日期 - 科室创建日期 */
    @TableField("created_date")
    private LocalDate createdDate;

    /** 最后审计日期 - 最后审计日期 */
    @TableField("last_audit_date")
    private LocalDate lastAuditDate;

}

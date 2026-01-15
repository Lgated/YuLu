package com.ityfz.yulu.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 租户实体，对应表 tenant。
 */
@Data
@TableName("tenant")
public class Tenant implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private String tenantCode;

    private String name;

    /**
     * 状态：1-启用 0-禁用。
     */
    private Integer status;

    private String remark;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    /**
     * 租户标识码（对外使用，比tenantCode更友好）
     * C端用户使用此字段登录，而不是tenantCode
     */
    @TableField("tenant_identifier")
    private String tenantIdentifier;
}













package com.ityfz.yulu.handoff.enums;

/**
 * 转人工事件类型枚举
 */
public enum HandoffEventType {


    /**
     * 创建
     */
    CREATED("CREATED", "创建"),

    /**
     * 分配
     */
    ASSIGNED("ASSIGNED", "分配"),

    /**
     * 接受
     */
    ACCEPTED("ACCEPTED", "接受"),

    /**
     * 拒绝
     */
    REJECTED("REJECTED", "拒绝"),

    /**
     * 开始
     */
    STARTED("STARTED", "开始"),

    /**
     * 完成
     */
    COMPLETED("COMPLETED", "完成"),

    /**
     * 关闭
     */
    CLOSED("CLOSED", "关闭");

    private final String code;
    private final String desc;

    HandoffEventType(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public String getCode() {
        return code;
    }

    public String getDesc() {
        return desc;
    }

}

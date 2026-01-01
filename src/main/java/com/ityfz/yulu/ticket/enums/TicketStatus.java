package com.ityfz.yulu.ticket.enums;

/**
 * 工单状态枚举
 */
public enum TicketStatus {

    /**
     * 待处理：工单刚创建，还未分配
     */
    PENDING("PENDING", "待处理"),

    /**
     * 处理中：已分配给客服，正在处理
     */
    PROCESSING("PROCESSING", "处理中"),

    /**
     * 已完成：问题已解决
     */
    DONE("DONE", "已完成"),

    /**
     * 已关闭：工单已关闭（可能是用户取消或其他原因）
     */
    CLOSED("CLOSED", "已关闭");

    private final String code;
    private final String desc;

    TicketStatus(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public String getCode() {
        return code;
    }

    public String getDesc() {
        return desc;
    }

    /**
     * 根据code获取枚举
     */
    public static TicketStatus fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (TicketStatus status : values()) {
            if (status.code.equalsIgnoreCase(code)) {
                return status;
            }
        }
        return null;
    }

    /**
     * 判断是否为有效状态
     */
    public static boolean isValid(String code) {
        return fromCode(code) != null;
    }
}

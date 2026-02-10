package com.ityfz.yulu.handoff.enums;

/**
 * 转人工状态枚举
 */
public enum HandoffStatus {


    /**
     * 排队中
     */
    PENDING("PENDING", "排队中"),

    /**
     * 已分配（等待客服接受）
     */
    ASSIGNED("ASSIGNED", "已分配"),

    /**
     * 已接受（客服已接受）
     */
    ACCEPTED("ACCEPTED", "已接受"),

    /**
     * 进行中（对话进行中）
     */
    IN_PROGRESS("IN_PROGRESS", "进行中"),

    /**
     * 已完成
     */
    COMPLETED("COMPLETED", "已完成"),

    /**
     * 已关闭
     */
    CLOSED("CLOSED", "已关闭"),

    /**
     * 已拒绝（客服拒绝）
     */
    REJECTED("REJECTED", "已拒绝"),

    /**
     * 客户取消
     */
    CANCELLED("CANCELLED","已取消" ),

    /**
     * 已转为工单（兜底）
     */
    FALLBACK_TICKET("FALLBACK_TICKET", "已转为工单");

    private final String code;
    private final String desc;

    HandoffStatus(String code, String desc) {
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
    public static HandoffStatus fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (HandoffStatus status : values()) {
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

    /**
     * 判断是否为已完成状态（包括所有终态状态）
     * 终态状态包括：
     * - COMPLETED: 正常完成
     * - CLOSED: 正常关闭
     * - CANCELLED: 用户取消
     * - REJECTED: 客服拒绝
     * - FALLBACK_TICKET: 已转为工单
     */
    public static boolean isCompleted(String code) {
        if (code == null) {
            return false;
        }

        // 只有这些状态是"进行中"的状态，其他都是终态
        return !PENDING.getCode().equals(code)
                && !ASSIGNED.getCode().equals(code)
                && !ACCEPTED.getCode().equals(code)
                && !IN_PROGRESS.getCode().equals(code);
    }

}

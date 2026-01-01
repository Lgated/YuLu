package com.ityfz.yulu.ticket.enums;

/**
 * 工单优先级枚举
 */
public enum TicketPriority {
    /**
     * 低优先级：一般咨询
     */
    LOW("LOW", "低"),

    /**
     * 中优先级：普通问题
     */
    MEDIUM("MEDIUM", "中"),

    /**
     * 高优先级：需要尽快处理
     */
    HIGH("HIGH", "高"),

    /**
     * 紧急：需要立即处理
     */
    URGENT("URGENT", "紧急");

    private final String code;
    private final String desc;

    TicketPriority(String code, String desc) {
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
    public static TicketPriority fromCode(String code) {
        if(code == null){
            return null;
        }

        for (TicketPriority priority : values()) {
            if (priority.code.equalsIgnoreCase(code)) {
                return priority;
            }
        }
        return null;
    }

    /**
     * 判断是否为有效优先级
     */
    public static boolean isValid(String code) {
        return fromCode(code) != null;
    }
}

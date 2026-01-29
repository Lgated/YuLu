package com.ityfz.yulu.handoff.enums;

/**
 * 操作人类型枚举
 */
public enum OperatorType {


    /**
     * 客户
     */
    USER("USER", "客户"),

    /**
     * 客服
     */
    AGENT("AGENT", "客服"),

    /**
     * 系统
     */
    SYSTEM("SYSTEM", "系统");

    private final String code;
    private final String desc;

    OperatorType(String code, String desc) {
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

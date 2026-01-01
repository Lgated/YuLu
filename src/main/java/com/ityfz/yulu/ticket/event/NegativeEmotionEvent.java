package com.ityfz.yulu.ticket.event;

import lombok.Data;

import java.io.Serializable;

/**
 * 负向情绪检测事件
 */
@Data
public class NegativeEmotionEvent implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long tenantId;
    private Long userId;
    private Long sessionId;
    private String question;
    private String priority;
    private String emotion; // NEGATIVE / ANGRY
    private Long timestamp;

    public NegativeEmotionEvent() {
        this.timestamp = System.currentTimeMillis();
    }
}

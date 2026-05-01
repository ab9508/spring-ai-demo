package com.example.ai.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 售后工单表
 */
@Data
public class AftersaleTicket {

    /**
     * 工单号，主键，格式TK-时间戳
     */
    private String ticketId;

    /**
     * 关联订单号
     */
    private String orderId;

    /**
     * 用户ID
     */
    private String userId;

    /**
     * 工单类型：退货/换货/投诉/补发
     */
    private String type;

    /**
     * 申请原因
     */
    private String reason;

    /**
     * 工单状态：待处理/处理中/已完成/已拒绝
     */
    private String status;

    /**
     * 处理结果
     */
    private String resolution;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
}
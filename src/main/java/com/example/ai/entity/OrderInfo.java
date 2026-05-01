package com.example.ai.entity;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订单信息表实体
 *
 * @author Java开发
 */
@Data
public class OrderInfo {

    /**
     * 订单号，主键
     */
    private String orderId;

    /**
     * 用户ID
     */
    private String userId;

    /**
     * 订单状态：待付款/已付款/已发货/已签收/已取消
     */
    private String status;

    /**
     * 物流公司和单号，如：顺丰速运 SF1234567890
     */
    private String logistics;

    /**
     * 物流状态：揽件/运输中/派送中/已签收
     */
    private String logisticsStatus;

    /**
     * 收货地址
     */
    private String address;

    /**
     * 预计送达时间描述
     */
    private String estimatedDelivery;

    /**
     * 订单总金额（元）
     */
    private BigDecimal totalAmount;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
}

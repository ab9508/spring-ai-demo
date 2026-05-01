package com.example.ai.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 商品信息表
 */
@Data
public class ProductInfo {

    /**
     * 商品ID，主键
     */
    private String productId;

    /**
     * 商品名称
     */
    private String productName;

    /**
     * 商品分类：图书/电子/服装/食品
     */
    private String category;

    /**
     * 库存数量
     */
    private Integer stock;

    /**
     * 商品单价（元）
     */
    private BigDecimal price;

    /**
     * 商品简介
     */
    private String description;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;
}
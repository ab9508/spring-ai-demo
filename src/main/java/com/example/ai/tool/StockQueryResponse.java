package com.example.ai.tool;

import lombok.Data;

/**
 * @author ab
 * @date 2026/4/28
 **/
@Data
public class StockQueryResponse {
    private String productId;
    private String productName;
    private int stock;
    private double price;
}



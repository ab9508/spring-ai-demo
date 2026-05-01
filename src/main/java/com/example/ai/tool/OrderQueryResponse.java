package com.example.ai.tool;

/**
 * Tool的返回结果 —— AI会读取这些信息来组织回答
 */
public class OrderQueryResponse {
    private String orderId;
    private String status;
    private String logistics;
    private String logisticsStatus;
    private String estimatedDelivery;
    private String address;
    private String totalAmount;

    public String getOrderId() { return orderId; }
    public void setOrderId(String orderId) { this.orderId = orderId; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getLogistics() { return logistics; }
    public void setLogistics(String logistics) { this.logistics = logistics; }

    public String getLogisticsStatus() { return logisticsStatus; }
    public void setLogisticsStatus(String logisticsStatus) { this.logisticsStatus = logisticsStatus; }

    public String getEstimatedDelivery() { return estimatedDelivery; }
    public void setEstimatedDelivery(String estimatedDelivery) { this.estimatedDelivery = estimatedDelivery; }

    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }

    public String getTotalAmount() { return totalAmount; }
    public void setTotalAmount(String totalAmount) { this.totalAmount = totalAmount; }
}

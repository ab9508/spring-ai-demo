package com.example.ai.tool;

import com.example.ai.dao.AftersaleDao;
import com.example.ai.dao.OrderDao;
import com.example.ai.dao.ProductDao;
import com.example.ai.entity.AftersaleTicket;
import com.example.ai.entity.OrderInfo;
import com.example.ai.entity.ProductInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 订单相关工具集（数据查DB版）
 * <p>
 * ============ Spring AI 1.0.x Tool Calling 新写法 ============
 * <p>
 * 新写法（1.0.x 标准）：
 *
 * @Component + @Tool 注解方法
 * chatClientBuilder.defaultTools(orderTools实例)  ← 传实例
 * <p>
 * 核心变化：
 * - 工具不再是 Spring Bean Function，而是普通类的普通方法
 * - @Tool(description = "...") 替代了 @Description
 * - 方法参数直接写，不需要包装成 Request record
 * - 返回值 AI 会自动序列化为 JSON 读取
 * <p>
 * ============ 数据源 ============
 * Tool 方法通过 DAO 层查询 PostgreSQL 数据库，不再使用硬编码 mock 数据。
 * 建表脚本：src/main/resources/init-data.sql
 */
@Slf4j
@Component
// mcp-client 模式下不加载 OrderTools（强制 Client 走 MCP 远程调用 Server）
// 默认模式(8080) 和 mcp-server(8081) 模式下继续使用本地 @Tool
@ConditionalOnProperty(name = "app.mcp.client.enabled", havingValue = "false", matchIfMissing = true)
public class OrderTools {

    private final OrderDao orderDao;
    private final ProductDao productDao;
    private final AftersaleDao aftersaleDao;

    public OrderTools(OrderDao orderDao, ProductDao productDao, AftersaleDao aftersaleDao) {
        this.orderDao = orderDao;
        this.productDao = productDao;
        this.aftersaleDao = aftersaleDao;
    }

    /**
     * 根据订单号查询订单状态
     *
     * @Tool description 是 AI 决定是否调用此方法的依据，要写清楚"什么情况下用"
     */
    @Tool(description = "根据订单号查询订单的当前状态、收货地址、物流信息。当用户询问订单状态、物流进度、订单详情时调用此工具。")
    public OrderQueryResponse queryOrder(String orderId) {
        log.info("【Tool调用】queryOrder 被调用，orderId={}", orderId);

        OrderQueryResponse response = new OrderQueryResponse();
        response.setOrderId(orderId);

        OrderInfo order = orderDao.findByOrderId(orderId);
        log.info("【Tool调用】订单信息:{}", order);
        if (order == null) {
            response.setStatus("未找到该订单，请确认订单号是否正确");
            return response;
        }

        response.setStatus(order.getStatus());
        response.setLogistics(order.getLogistics());
        response.setLogisticsStatus(order.getLogisticsStatus());
        response.setEstimatedDelivery(order.getEstimatedDelivery());
        response.setAddress(order.getAddress());
        response.setTotalAmount(order.getTotalAmount() != null ? order.getTotalAmount().toString() : null);
        return response;
    }

    /**
     * 查询商品库存信息
     * 支持按商品ID精确查询，也支持按名称关键词模糊搜索
     */
    @Tool(description = "查询商品库存、价格等信息。当用户询问商品库存、商品价格、商品信息时调用此工具。")
    public String queryStock(String productIdOrName) {
        log.info("【Tool调用】queryStock 被调用，productIdOrName={}", productIdOrName);

        // 先尝试按ID精确查询
        ProductInfo product = productDao.findByProductId(productIdOrName);
        if (product != null) {
            return formatProduct(product);
        }

        // ID查不到，按名称模糊搜索
        List<ProductInfo> list = productDao.findByProductNameLike(productIdOrName);
        if (list.isEmpty()) {
            return "未找到相关商品，请确认商品ID或名称。";
        }
        if (list.size() == 1) {
            return formatProduct(list.get(0));
        }
        // 多条结果，返回列表摘要
        StringBuilder sb = new StringBuilder("找到 " + list.size() + " 个相关商品：\n");
        for (ProductInfo p : list) {
            sb.append("- ").append(p.getProductName())
                    .append("（").append(p.getProductId()).append("）")
                    .append(" 库存:").append(p.getStock())
                    .append(" 价格:").append(p.getPrice()).append("元\n");
        }
        return sb.toString();
    }

    /**
     * 查询/提交售后工单
     */
    @Tool(description = "查询或提交售后工单。当用户询问售后进度、退换货状态，或需要退换货、投诉、申请补偿时调用此工具。")
    public String handleAftersale(String orderId, String type, String reason) {
        log.info("【Tool调用】handleAftersale 被调用，orderId={}, type={}, reason={}", orderId, type, reason);

        // 如果只是查询（没传type和reason），返回该订单的售后记录
        if (type == null || type.isBlank()) {
            List<AftersaleTicket> tickets = aftersaleDao.findByOrderId(orderId);
            log.info("【Tool调用】handleAftersale 被调用，查询到DB数据量:{}", tickets.size());
            if (tickets.isEmpty()) {
                return "订单 " + orderId + " 没有售后记录。";
            }
            StringBuilder sb = new StringBuilder("订单 " + orderId + " 的售后记录：\n");
            for (AftersaleTicket t : tickets) {
                sb.append("- 工单号: ").append(t.getTicketId())
                        .append(" 类型: ").append(t.getType())
                        .append(" 状态: ").append(t.getStatus());
                if (t.getResolution() != null) {
                    sb.append(" 处理结果: ").append(t.getResolution());
                }
                sb.append("\n");
            }
            return sb.toString();
        }
        log.info("【Tool调用】handleAftersale 被调用，没查询到售后记录，现在提交新的工单");
        // 提交新工单
        AftersaleTicket ticket = aftersaleDao.createTicket(orderId, "U1001", type, reason);
        return "售后工单已提交，工单号：" + ticket.getTicketId()
                + "（" + ticket.getType() + "），我们将在24小时内处理。";
    }

    private String formatProduct(ProductInfo p) {
        StringBuilder sb = new StringBuilder();
        sb.append("商品：").append(p.getProductName())
                .append("（").append(p.getProductId()).append("）\n");
        sb.append("分类：").append(p.getCategory()).append("\n");
        sb.append("价格：").append(p.getPrice()).append("元\n");
        sb.append("库存：").append(p.getStock()).append("件");
        if (p.getStock() == 0) {
            sb.append("（已售罄）");
        }
        if (p.getDescription() != null) {
            sb.append("\n简介：").append(p.getDescription());
        }
        return sb.toString();
    }
}

package com.example.ai.tool;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

/**
 * 订单相关工具集
 * <p>
 * ============ Spring AI 1.0.x Tool Calling 新写法 ============
 * <p>
 * 旧写法（已废弃，会报 "No @Tool annotated methods found"）：
 *
 * @Configuration + @Bean + @Description + Function<Request, Response>
 * chatClientBuilder.defaultTools("queryOrder")  ← Bean名称字符串
 * <p>
 * 新写法（1.0.x 标准）：
 * @Component + @Tool 注解方法
 * chatClientBuilder.defaultTools(OrderTools.class)  ← 传 Class
 * <p>
 * 核心变化：
 * - 工具不再是 Spring Bean Function，而是普通类的普通方法
 * - @Tool(description = "...") 替代了 @Description
 * - 方法参数直接写，不需要包装成 Request record
 * - 返回值 AI 会自动序列化为 JSON 读取
 */
@Component
public class OrderTools {

    /**
     * 根据订单号查询订单状态
     *
     * @Tool description 是 AI 决定是否调用此方法的依据，要写清楚"什么情况下用"
     */
    @Tool(description = "根据订单号查询订单的当前状态、收货地址、物流信息。当用户询问订单状态、物流进度、订单详情时调用此工具。")
    public OrderQueryResponse queryOrder(String orderId) {
        System.out.println("【Tool调用】queryOrder 被调用，orderId=" + orderId);

        OrderQueryResponse response = new OrderQueryResponse();
        response.setOrderId(orderId);

        if ("ORD-001".equals(orderId)) {
            response.setStatus("已发货");
            response.setLogistics("顺丰速运 SF1234567890");
            response.setEstimatedDelivery("明天14:00前");
            response.setAddress("深圳市南山区科技园XX号");
        } else {
            response.setStatus("未找到该订单，请确认订单号是否正确");
        }

        return response;
    }
// 编译爆红不影响运行
    //http://localhost:8080/agent/chat?message=帮我查下商品iD001的库存
public StockQueryResponse queryStock(StockQueryRequest request) {
        StockQueryResponse resp = new StockQueryResponse();
        resp.setProductId(request.getProductId());
        resp.setProductName("Java高级编程（第4版）");
        resp.setStock(156);
        resp.setPrice(89.00);
        return resp;
    }

    //http://127.0.0.1:8080/agent/chat?message=002单子的售后
    @Tool(description = "提交售后工单。当用户需要退换货、投诉、申请补偿时调用。")
    public AftersaleResponse submitAftersale(AftersaleRequest request) {
        // 模拟创建工单
        AftersaleResponse resp = new AftersaleResponse();
        resp.setTicketId("TK-" + System.currentTimeMillis());
        resp.setMessage("售后工单已提交，工单号：" + resp.getTicketId()
                + "，我们将在24小时内处理。");
        return resp;
    }
}

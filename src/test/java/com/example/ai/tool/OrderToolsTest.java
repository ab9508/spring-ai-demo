package com.example.ai.tool;

import com.example.ai.dao.AftersaleDao;
import com.example.ai.dao.OrderDao;
import com.example.ai.dao.ProductDao;
import com.example.ai.entity.AftersaleTicket;
import com.example.ai.entity.OrderInfo;
import com.example.ai.entity.ProductInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * OrderTools 单元测试
 * <p>
 * 覆盖场景：
 * 1. queryOrder — 订单存在/不存在
 * 2. queryStock — ID精确查询/名称模糊搜索/未找到
 * 3. handleAftersale — 查询售后记录/提交新工单
 * 4. formatProduct — 库存为0时显示已售罄
 */
@ExtendWith(MockitoExtension.class)
class OrderToolsTest {

    @Mock
    private OrderDao orderDao;

    @Mock
    private ProductDao productDao;

    @Mock
    private AftersaleDao aftersaleDao;

    private OrderTools orderTools;

    @BeforeEach
    void setUp() {
        orderTools = new OrderTools(orderDao, productDao, aftersaleDao);
    }

    // ==================== queryOrder ====================

    @Test
    @DisplayName("查询存在的订单应返回完整订单信息")
    void queryOrderShouldReturnFullInfoWhenOrderExists() {
        // 准备
        String orderId = "ORD-001";
        OrderInfo mockOrder = new OrderInfo();
        mockOrder.setOrderId(orderId);
        mockOrder.setStatus("已发货");
        mockOrder.setLogistics("顺丰速运");
        mockOrder.setLogisticsStatus("运输中");
        mockOrder.setEstimatedDelivery("预计明天送达");
        mockOrder.setAddress("广东省深圳市南山区科技园");
        mockOrder.setTotalAmount(new BigDecimal("299.00"));
        when(orderDao.findByOrderId(orderId)).thenReturn(mockOrder);

        // 执行
        OrderQueryResponse response = orderTools.queryOrder(orderId);

        // 验证
        assertNotNull(response, "返回结果不应为null");
        assertEquals(orderId, response.getOrderId());
        assertEquals("已发货", response.getStatus());
        assertEquals("顺丰速运", response.getLogistics());
        assertEquals("运输中", response.getLogisticsStatus());
        assertEquals("预计明天送达", response.getEstimatedDelivery());
        assertEquals("广东省深圳市南山区科技园", response.getAddress());
        assertEquals("299.00", response.getTotalAmount());
    }

    @Test
    @DisplayName("查询不存在的订单应返回提示信息")
    void queryOrderShouldReturnWarningWhenOrderNotFound() {
        // 准备
        String orderId = "ORD-NOT-EXIST";
        when(orderDao.findByOrderId(orderId)).thenReturn(null);

        // 执行
        OrderQueryResponse response = orderTools.queryOrder(orderId);

        // 验证
        assertNotNull(response, "返回结果不应为null");
        assertEquals(orderId, response.getOrderId());
        assertTrue(response.getStatus().contains("未找到该订单"),
                "订单不存在时应返回提示信息");
        assertNull(response.getTotalAmount(), "不存在订单的金额应为null");
    }

    @Test
    @DisplayName("totalAmount 为 null 时不应引发 NPE")
    void queryOrderShouldHandleNullTotalAmount() {
        // 准备
        String orderId = "ORD-NULL-AMOUNT";
        OrderInfo mockOrder = new OrderInfo();
        mockOrder.setOrderId(orderId);
        mockOrder.setStatus("待付款");
        mockOrder.setTotalAmount(null); // 金额为null
        when(orderDao.findByOrderId(orderId)).thenReturn(mockOrder);

        // 执行
        OrderQueryResponse response = orderTools.queryOrder(orderId);

        // 验证
        assertNotNull(response);
        assertNull(response.getTotalAmount(), "金额为null时应返回null");
    }

    // ==================== queryStock ====================

    @Test
    @DisplayName("按商品ID精确查询应返回详细商品信息")
    void queryStockShouldReturnProductById() {
        // 准备
        String productId = "P001";
        ProductInfo mockProduct = new ProductInfo();
        mockProduct.setProductId(productId);
        mockProduct.setProductName("Java编程思想");
        mockProduct.setCategory("图书");
        mockProduct.setStock(100);
        mockProduct.setPrice(new BigDecimal("89.00"));
        mockProduct.setDescription("经典Java入门书籍");
        when(productDao.findByProductId(productId)).thenReturn(mockProduct);

        // 执行
        String result = orderTools.queryStock(productId);

        // 验证
        assertNotNull(result);
        assertTrue(result.contains("Java编程思想"), "应包含商品名称");
        assertTrue(result.contains("图书"), "应包含商品分类");
        assertTrue(result.contains("89.00"), "应包含商品价格");
        assertTrue(result.contains("100"), "应包含库存数量");
        assertTrue(result.contains("经典Java入门书籍"), "应包含商品简介");
    }

    @Test
    @DisplayName("库存为0时应显示已售罄标签")
    void queryStockShouldShowSoldOutWhenStockIsZero() {
        // 准备
        String productId = "P002";
        ProductInfo mockProduct = new ProductInfo();
        mockProduct.setProductId(productId);
        mockProduct.setProductName("限量版模型");
        mockProduct.setCategory("玩具");
        mockProduct.setStock(0);
        mockProduct.setPrice(new BigDecimal("199.00"));
        when(productDao.findByProductId(productId)).thenReturn(mockProduct);

        // 执行
        String result = orderTools.queryStock(productId);

        // 验证
        assertTrue(result.contains("已售罄"), "库存为0时应显示已售罄");
    }

    @Test
    @DisplayName("ID查不到时应按名称模糊搜索")
    void queryStockShouldFallbackToNameSearch() {
        // 准备
        String keyword = "编程";
        when(productDao.findByProductId(keyword)).thenReturn(null);
        ProductInfo product1 = new ProductInfo();
        product1.setProductId("P001");
        product1.setProductName("Java编程思想");
        product1.setStock(50);
        product1.setPrice(new BigDecimal("89.00"));
        when(productDao.findByProductNameLike(keyword))
                .thenReturn(Collections.singletonList(product1));

        // 执行
        String result = orderTools.queryStock(keyword);

        // 验证
        assertTrue(result.contains("Java编程思想"), "模糊搜索匹配时应返回商品");
    }

    @Test
    @DisplayName("模糊搜索多条结果应返回列表摘要")
    void queryStockShouldReturnSummaryForMultipleResults() {
        // 准备
        String keyword = "手机";
        when(productDao.findByProductId(keyword)).thenReturn(null);
        ProductInfo p1 = new ProductInfo();
        p1.setProductId("P003"); p1.setProductName("iPhone 15"); p1.setStock(10); p1.setPrice(new BigDecimal("6999.00"));
        ProductInfo p2 = new ProductInfo();
        p2.setProductId("P004"); p2.setProductName("华为 Mate 60"); p2.setStock(20); p2.setPrice(new BigDecimal("5999.00"));
        when(productDao.findByProductNameLike(keyword)).thenReturn(List.of(p1, p2));

        // 执行
        String result = orderTools.queryStock(keyword);

        // 验证
        assertTrue(result.contains("2 个相关商品"), "多条结果应显示数量");
        assertTrue(result.contains("iPhone 15"), "应列出第一个商品");
        assertTrue(result.contains("华为 Mate 60"), "应列出第二个商品");
    }

    @Test
    @DisplayName("ID和名称都查不到时应返回未找到提示")
    void queryStockShouldReturnNotFoundWhenNothingMatches() {
        // 准备
        String keyword = "不存在的商品";
        when(productDao.findByProductId(keyword)).thenReturn(null);
        when(productDao.findByProductNameLike(keyword)).thenReturn(Collections.emptyList());

        // 执行
        String result = orderTools.queryStock(keyword);

        // 验证
        assertTrue(result.contains("未找到"), "完全查不到时应返回提示");
    }

    // ==================== handleAftersale ====================

    @Test
    @DisplayName("查询售后记录：有记录时应返回工单列表")
    void handleAftersaleShouldReturnTicketsWhenQuerying() {
        // 准备：未传 type 和 reason，视为查询
        String orderId = "ORD-001";
        AftersaleTicket ticket = new AftersaleTicket();
        ticket.setTicketId("TK-001");
        ticket.setType("退货");
        ticket.setStatus("处理中");
        ticket.setResolution(null); // 未处理完，没有resolution
        when(aftersaleDao.findByOrderId(orderId)).thenReturn(List.of(ticket));

        // 执行
        String result = orderTools.handleAftersale(orderId, null, null);

        // 验证
        assertTrue(result.contains("ORD-001"), "应包含订单号");
        assertTrue(result.contains("TK-001"), "应包含工单号");
        assertTrue(result.contains("退货"), "应包含工单类型");
        assertTrue(result.contains("处理中"), "应包含工单状态");
    }

    @Test
    @DisplayName("查询售后记录：无记录时应提示无记录")
    void handleAftersaleShouldReturnNoRecordsWhenEmpty() {
        // 准备
        String orderId = "ORD-NO-AFTERSALE";
        when(aftersaleDao.findByOrderId(orderId)).thenReturn(Collections.emptyList());

        // 执行
        String result = orderTools.handleAftersale(orderId, null, null);

        // 验证
        assertTrue(result.contains("没有售后记录"), "无记录时应提示");
    }

    @Test
    @DisplayName("提交售后工单应返回工单号和提示")
    void handleAftersaleShouldCreateTicket() {
        // 准备：传了 type 和 reason，视为提交
        String orderId = "ORD-001";
        String type = "退货";
        String reason = "商品有质量问题";

        AftersaleTicket createdTicket = new AftersaleTicket();
        createdTicket.setTicketId("TK-NEW-001");
        createdTicket.setType(type);
        createdTicket.setStatus("待处理");
        when(aftersaleDao.createTicket(orderId, "U1001", type, reason))
                .thenReturn(createdTicket);

        // 执行
        String result = orderTools.handleAftersale(orderId, type, reason);

        // 验证
        assertTrue(result.contains("售后工单已提交"), "提交成功应返回确认信息");
        assertTrue(result.contains("TK-NEW-001"), "应返回新工单号");
        assertTrue(result.contains("退货"), "应返回工单类型");
    }

    @Test
    @DisplayName("type 为空白字符串时视为查询")
    void handleAftersaleShouldTreatBlankTypeAsQuery() {
        // 准备
        String orderId = "ORD-001";
        when(aftersaleDao.findByOrderId(orderId)).thenReturn(Collections.emptyList());

        // 执行：type 为空白字符串
        String result = orderTools.handleAftersale(orderId, "  ", null);

        // 验证：走查询逻辑而非创建
        assertTrue(result.contains("没有售后记录"), "空白type应视为查询");
        verify(aftersaleDao, never()).createTicket(anyString(), anyString(), anyString(), anyString());
    }
}

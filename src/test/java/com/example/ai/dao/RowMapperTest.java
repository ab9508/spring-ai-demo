package com.example.ai.dao;

import com.example.ai.entity.AftersaleTicket;
import com.example.ai.entity.OrderInfo;
import com.example.ai.entity.ProductInfo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.RowMapper;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * DAO 层 RowMapper 映射测试
 * <p>
 * 验证 OrderDao / ProductDao / AftersaleDao 的 ResultSet → 实体映射逻辑正确。
 * <p>
 * 对应功能测试案例：TC-06 ~ TC-09（工具调用需要 DAO 正确映射数据）
 */
class RowMapperTest {

    @Nested
    @DisplayName("OrderDao RowMapper — 订单字段映射")
    class OrderDaoRowMapperTest {

        @Test
        @DisplayName("RowMapper 应正确映射所有订单字段")
        void shouldMapAllOrderFields() throws Exception {
            ResultSet rs = mockOrderResultSet();

            var mapperField = OrderDao.class.getDeclaredField("ROW_MAPPER");
            mapperField.setAccessible(true);
            @SuppressWarnings("unchecked")
            RowMapper<OrderInfo> rowMapper = (RowMapper<OrderInfo>) mapperField.get(null);

            OrderInfo order = rowMapper.mapRow(rs, 1);

            assertEquals("ORD-001", order.getOrderId());
            assertEquals("U1001", order.getUserId());
            assertEquals("已发货", order.getStatus());
            assertEquals("顺丰速运", order.getLogistics());
            assertEquals("运输中", order.getLogisticsStatus());
            assertEquals("深圳科技园", order.getAddress());
            assertEquals("预计明天", order.getEstimatedDelivery());
            assertEquals(new BigDecimal("299.00"), order.getTotalAmount());
        }

        private ResultSet mockOrderResultSet() throws Exception {
            ResultSet rs = mock(ResultSet.class);
            when(rs.getString("order_id")).thenReturn("ORD-001");
            when(rs.getString("user_id")).thenReturn("U1001");
            when(rs.getString("status")).thenReturn("已发货");
            when(rs.getString("logistics")).thenReturn("顺丰速运");
            when(rs.getString("logistics_status")).thenReturn("运输中");
            when(rs.getString("address")).thenReturn("深圳科技园");
            when(rs.getString("estimated_delivery")).thenReturn("预计明天");
            when(rs.getBigDecimal("total_amount")).thenReturn(new BigDecimal("299.00"));
            when(rs.getTimestamp("create_time")).thenReturn(Timestamp.valueOf(LocalDateTime.now()));
            when(rs.getTimestamp("update_time")).thenReturn(Timestamp.valueOf(LocalDateTime.now()));
            return rs;
        }
    }

    @Nested
    @DisplayName("ProductDao RowMapper — 商品字段映射")
    class ProductDaoRowMapperTest {

        @Test
        @DisplayName("RowMapper 应正确映射所有商品字段")
        void shouldMapAllProductFields() throws Exception {
            ResultSet rs = mockProductResultSet();

            var mapperField = ProductDao.class.getDeclaredField("ROW_MAPPER");
            mapperField.setAccessible(true);
            @SuppressWarnings("unchecked")
            RowMapper<ProductInfo> rowMapper = (RowMapper<ProductInfo>) mapperField.get(null);

            ProductInfo product = rowMapper.mapRow(rs, 1);

            assertEquals("P001", product.getProductId());
            assertEquals("Java编程思想", product.getProductName());
            assertEquals("图书", product.getCategory());
            assertEquals(100, product.getStock());
            assertEquals(new BigDecimal("89.00"), product.getPrice());
            assertEquals("经典Java书", product.getDescription());
        }

        private ResultSet mockProductResultSet() throws Exception {
            ResultSet rs = mock(ResultSet.class);
            when(rs.getString("product_id")).thenReturn("P001");
            when(rs.getString("product_name")).thenReturn("Java编程思想");
            when(rs.getString("category")).thenReturn("图书");
            when(rs.getInt("stock")).thenReturn(100);
            when(rs.getBigDecimal("price")).thenReturn(new BigDecimal("89.00"));
            when(rs.getString("description")).thenReturn("经典Java书");
            return rs;
        }
    }

    @Nested
    @DisplayName("AftersaleDao RowMapper — 售后工单字段映射")
    class AftersaleDaoRowMapperTest {

        @Test
        @DisplayName("RowMapper 应正确映射所有售后工单字段")
        void shouldMapAllAftersaleFields() throws Exception {
            ResultSet rs = mockAftersaleResultSet();

            var mapperField = AftersaleDao.class.getDeclaredField("ROW_MAPPER");
            mapperField.setAccessible(true);
            @SuppressWarnings("unchecked")
            RowMapper<AftersaleTicket> rowMapper = (RowMapper<AftersaleTicket>) mapperField.get(null);

            AftersaleTicket ticket = rowMapper.mapRow(rs, 1);

            assertEquals("TK-001", ticket.getTicketId());
            assertEquals("ORD-001", ticket.getOrderId());
            assertEquals("U1001", ticket.getUserId());
            assertEquals("退货", ticket.getType());
            assertEquals("质量问题", ticket.getReason());
            assertEquals("处理中", ticket.getStatus());
            assertEquals("已换货", ticket.getResolution());
        }

        private ResultSet mockAftersaleResultSet() throws Exception {
            ResultSet rs = mock(ResultSet.class);
            when(rs.getString("ticket_id")).thenReturn("TK-001");
            when(rs.getString("order_id")).thenReturn("ORD-001");
            when(rs.getString("user_id")).thenReturn("U1001");
            when(rs.getString("type")).thenReturn("退货");
            when(rs.getString("reason")).thenReturn("质量问题");
            when(rs.getString("status")).thenReturn("处理中");
            when(rs.getString("resolution")).thenReturn("已换货");
            when(rs.getTimestamp("create_time")).thenReturn(Timestamp.valueOf(LocalDateTime.now()));
            when(rs.getTimestamp("update_time")).thenReturn(Timestamp.valueOf(LocalDateTime.now()));
            return rs;
        }
    }
}

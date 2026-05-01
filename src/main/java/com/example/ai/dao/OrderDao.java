package com.example.ai.dao;

import com.example.ai.entity.OrderInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

/**
 * 订单数据访问层（JdbcTemplate）
 */
@Slf4j
@Repository
public class OrderDao {

    private final JdbcTemplate jdbcTemplate;

    public OrderDao(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private static final RowMapper<OrderInfo> ROW_MAPPER = (rs, rowNum) -> {
        OrderInfo order = new OrderInfo();
        order.setOrderId(rs.getString("order_id"));
        order.setUserId(rs.getString("user_id"));
        order.setStatus(rs.getString("status"));
        order.setLogistics(rs.getString("logistics"));
        order.setLogisticsStatus(rs.getString("logistics_status"));
        order.setAddress(rs.getString("address"));
        order.setEstimatedDelivery(rs.getString("estimated_delivery"));
        order.setTotalAmount(rs.getBigDecimal("total_amount"));
        order.setCreateTime(rs.getTimestamp("create_time").toLocalDateTime());
        order.setUpdateTime(rs.getTimestamp("update_time").toLocalDateTime());
        return order;
    };

    /**
     * 根据订单号查询
     */
    public OrderInfo findByOrderId(String orderId) {
        String sql = "SELECT * FROM order_info WHERE order_id = ?";
        List<OrderInfo> list = jdbcTemplate.query(sql, ROW_MAPPER, orderId);
        if (list.isEmpty()) {
            log.info("【OrderDao】未找到订单 orderId={}", orderId);
            return null;
        }
        log.info("【OrderDao】查询到订单 orderId={}, status={}", orderId, list.get(0).getStatus());
        return list.get(0);
    }
}

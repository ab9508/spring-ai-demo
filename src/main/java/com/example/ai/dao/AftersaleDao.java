package com.example.ai.dao;

import com.example.ai.entity.AftersaleTicket;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 售后工单数据访问层（JdbcTemplate）
 */
@Slf4j
@Repository
public class AftersaleDao {

    private final JdbcTemplate jdbcTemplate;

    public AftersaleDao(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private static final RowMapper<AftersaleTicket> ROW_MAPPER = (rs, rowNum) -> {
        AftersaleTicket ticket = new AftersaleTicket();
        ticket.setTicketId(rs.getString("ticket_id"));
        ticket.setOrderId(rs.getString("order_id"));
        ticket.setUserId(rs.getString("user_id"));
        ticket.setType(rs.getString("type"));
        ticket.setReason(rs.getString("reason"));
        ticket.setStatus(rs.getString("status"));
        ticket.setResolution(rs.getString("resolution"));
        ticket.setCreateTime(rs.getTimestamp("create_time").toLocalDateTime());
        ticket.setUpdateTime(rs.getTimestamp("update_time").toLocalDateTime());
        return ticket;
    };

    /**
     * 根据订单号查询售后工单
     */
    public List<AftersaleTicket> findByOrderId(String orderId) {
        String sql = "SELECT * FROM aftersale_ticket WHERE order_id = ? ORDER BY create_time DESC";
        List<AftersaleTicket> list = jdbcTemplate.query(sql, ROW_MAPPER, orderId);
        log.info("【AftersaleDao】查询工单 orderId={}, 数量={}", orderId, list.size());
        return list;
    }

    /**
     * 创建售后工单
     */
    public AftersaleTicket createTicket(String orderId, String userId, String type, String reason) {
        String ticketId = "TK-" + System.currentTimeMillis();
        String sql = "INSERT INTO aftersale_ticket (ticket_id, order_id, user_id, type, reason) VALUES (?, ?, ?, ?, ?)";
        jdbcTemplate.update(sql, ticketId, orderId, userId, type, reason);
        log.info("【AftersaleDao】创建工单 ticketId={}, orderId={}, type={}", ticketId, orderId, type);

        AftersaleTicket ticket = new AftersaleTicket();
        ticket.setTicketId(ticketId);
        ticket.setOrderId(orderId);
        ticket.setUserId(userId);
        ticket.setType(type);
        ticket.setReason(reason);
        ticket.setStatus("待处理");
        return ticket;
    }
}

package com.example.ai.dao;

import com.example.ai.entity.ProductInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

/**
 * 商品数据访问层（JdbcTemplate）
 */
@Slf4j
@Repository
public class ProductDao {

    private final JdbcTemplate jdbcTemplate;

    public ProductDao(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private static final RowMapper<ProductInfo> ROW_MAPPER = (rs, rowNum) -> {
        ProductInfo product = new ProductInfo();
        product.setProductId(rs.getString("product_id"));
        product.setProductName(rs.getString("product_name"));
        product.setCategory(rs.getString("category"));
        product.setStock(rs.getInt("stock"));
        product.setPrice(rs.getBigDecimal("price"));
        product.setDescription(rs.getString("description"));
        return product;
    };

    /**
     * 根据商品ID查询
     */
    public ProductInfo findByProductId(String productId) {
        String sql = "SELECT * FROM product_info WHERE product_id = ?";
        List<ProductInfo> list = jdbcTemplate.query(sql, ROW_MAPPER, productId);
        if (list.isEmpty()) {
            log.info("【ProductDao】未找到商品 productId={}", productId);
            return null;
        }
        log.info("【ProductDao】查询到商品 productId={}, name={}, stock={}", productId,
                list.get(0).getProductName(), list.get(0).getStock());
        return list.get(0);
    }

    /**
     * 根据商品名称模糊查询（支持AI用名称搜索）
     */
    public List<ProductInfo> findByProductNameLike(String keyword) {
        String sql = "SELECT * FROM product_info WHERE product_name LIKE ?";
        List<ProductInfo> list = jdbcTemplate.query(sql, ROW_MAPPER, "%" + keyword + "%");
        log.info("【ProductDao】模糊查询 keyword={}, 结果数量={}", keyword, list.size());
        return list;
    }
}

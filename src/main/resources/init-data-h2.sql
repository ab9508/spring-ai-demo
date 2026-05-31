-- H2 内存数据库初始化脚本（MCP Server profile 专用）
-- H2 兼容模式：spring.datasource.url 中已加 MODE=PostgreSQL

-- 1. 订单表
DROP TABLE IF EXISTS order_info CASCADE;
CREATE TABLE order_info (
    order_id       VARCHAR(20) PRIMARY KEY,
    user_id        VARCHAR(20) NOT NULL,
    status         VARCHAR(20) NOT NULL,
    logistics      VARCHAR(100),
    logistics_status VARCHAR(50),
    address        VARCHAR(200) NOT NULL,
    estimated_delivery VARCHAR(100),
    total_amount   DECIMAL(10,2) NOT NULL,
    create_time    TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time    TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 2. 商品表
DROP TABLE IF EXISTS product_info CASCADE;
CREATE TABLE product_info (
    product_id     VARCHAR(20) PRIMARY KEY,
    product_name   VARCHAR(100) NOT NULL,
    category       VARCHAR(50),
    stock          INT NOT NULL DEFAULT 0,
    price          DECIMAL(10,2) NOT NULL,
    description    VARCHAR(500),
    create_time    TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 3. 售后工单表
DROP TABLE IF EXISTS aftersale_ticket CASCADE;
CREATE TABLE aftersale_ticket (
    ticket_id      VARCHAR(30) PRIMARY KEY,
    order_id       VARCHAR(20) NOT NULL,
    user_id        VARCHAR(20) NOT NULL,
    type           VARCHAR(20) NOT NULL,
    reason         VARCHAR(500),
    status         VARCHAR(20) NOT NULL DEFAULT '待处理',
    resolution     VARCHAR(500),
    create_time    TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time    TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 模拟数据
INSERT INTO order_info (order_id, user_id, status, logistics, logistics_status, address, estimated_delivery, total_amount) VALUES
('ORD-001', 'U1001', '已发货', '顺丰速运 SF1234567890', '运输中', '深圳市南山区科技园XX号', '2026-05-02 14:00前', 189.00),
('ORD-002', 'U1001', '已签收', '中通快递 ZT9876543210', '已签收', '深圳市南山区科技园XX号', '2026-04-28 18:00前', 56.50),
('ORD-003', 'U1002', '待付款', NULL, NULL, '广州市天河区体育西路YY号', '付款后3天内发货', 299.00),
('ORD-004', 'U1001', '已发货', '京东物流 JD2026043000111', '派送中', '深圳市南山区科技园XX号', '2026-04-30 20:00前', 428.00),
('ORD-005', 'U1003', '已取消', NULL, NULL, '上海市浦东新区张江路ZZ号', NULL, 68.00);

INSERT INTO product_info (product_id, product_name, category, stock, price, description) VALUES
('PID-001', 'Java高级编程（第4版）', '图书', 156, 89.00, 'Java经典技术书籍'),
('PID-002', '罗技MX Master 3S鼠标', '电子', 42, 599.00, '静音点击，多设备切换'),
('PID-003', '优衣库纯棉T恤（白色）', '服装', 500, 79.00, '100%纯棉，透气舒适'),
('PID-004', '三只松鼠坚果礼盒', '食品', 88, 128.00, '含8种坚果，760g'),
('PID-005', 'Spring AI实战指南', '图书', 0, 69.00, 'Spring AI框架入门到实战');

INSERT INTO aftersale_ticket (ticket_id, order_id, user_id, type, reason, status, resolution) VALUES
('TK-2026042801', 'ORD-002', 'U1001', '换货', 'T恤尺码不合适', '已完成', '已安排换货'),
('TK-2026042901', 'ORD-001', 'U1001', '投诉', '物流无更新', '处理中', NULL),
('TK-2026043001', 'ORD-004', 'U1001', '退货', '鼠标滚轮问题', '待处理', NULL);

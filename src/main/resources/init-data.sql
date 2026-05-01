-- ============================================================
-- Spring AI Demo - 业务数据初始化脚本
-- 数据库：PostgreSQL（与 pgvector 共用 mydb）
-- 执行方式：psql -U postgres -d mydb -f init-data.sql
-- ============================================================

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
-- 不支持行内注释，需要单独语句
COMMENT ON TABLE order_info IS '订单信息表';
COMMENT ON COLUMN order_info.order_id IS '订单号，主键';
COMMENT ON COLUMN order_info.user_id IS '用户ID';
COMMENT ON COLUMN order_info.status IS '订单状态：待付款/已付款/已发货/已签收/已取消';
COMMENT ON COLUMN order_info.logistics IS '物流公司和单号，如：顺丰速运 SF1234567890';
COMMENT ON COLUMN order_info.logistics_status IS '物流状态：揽件/运输中/派送中/已签收';
COMMENT ON COLUMN order_info.address IS '收货地址';
COMMENT ON COLUMN order_info.estimated_delivery IS '预计送达时间描述';
COMMENT ON COLUMN order_info.total_amount IS '订单总金额（元）';
COMMENT ON COLUMN order_info.create_time IS '创建时间';
COMMENT ON COLUMN order_info.update_time IS '更新时间';

-- 2. 商品表
DROP TABLE IF EXISTS product_info CASCADE;
CREATE TABLE product_info (
    product_id     VARCHAR(20) PRIMARY KEY,
    product_name   VARCHAR(100) NOT NULL,
    category       VARCHAR(50),
    stock          INT NOT NULL DEFAULT 0,
    price          DECIMAL(10,2) NOT NULL,
    description    TEXT,
    create_time    TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE product_info IS '商品信息表';
COMMENT ON COLUMN product_info.product_id IS '商品ID，主键';
COMMENT ON COLUMN product_info.product_name IS '商品名称';
COMMENT ON COLUMN product_info.category IS '商品分类：图书/电子/服装/食品';
COMMENT ON COLUMN product_info.stock IS '库存数量';
COMMENT ON COLUMN product_info.price IS '商品单价（元）';
COMMENT ON COLUMN product_info.description IS '商品简介';
COMMENT ON COLUMN product_info.create_time IS '创建时间';

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

COMMENT ON TABLE aftersale_ticket IS '售后工单表';
COMMENT ON COLUMN aftersale_ticket.ticket_id IS '工单号，主键，格式TK-时间戳';
COMMENT ON COLUMN aftersale_ticket.order_id IS '关联订单号';
COMMENT ON COLUMN aftersale_ticket.user_id IS '用户ID';
COMMENT ON COLUMN aftersale_ticket.type IS '工单类型：退货/换货/投诉/补发';
COMMENT ON COLUMN aftersale_ticket.reason IS '申请原因';
COMMENT ON COLUMN aftersale_ticket.status IS '工单状态：待处理/处理中/已完成/已拒绝';
COMMENT ON COLUMN aftersale_ticket.resolution IS '处理结果';
COMMENT ON COLUMN aftersale_ticket.create_time IS '创建时间';
COMMENT ON COLUMN aftersale_ticket.update_time IS '更新时间';

-- ============================================================
-- 模拟数据
-- ============================================================

-- 订单数据（5条，覆盖各种状态）
INSERT INTO order_info (order_id, user_id, status, logistics, logistics_status, address, estimated_delivery, total_amount) VALUES
('ORD-001', 'U1001', '已发货', '顺丰速运 SF1234567890', '运输中', '深圳市南山区科技园XX号', '2026-05-02 14:00前', 189.00),
('ORD-002', 'U1001', '已签收', '中通快递 ZT9876543210', '已签收', '深圳市南山区科技园XX号', '2026-04-28 18:00前', 56.50),
('ORD-003', 'U1002', '待付款', NULL, NULL, '广州市天河区体育西路YY号', '付款后3天内发货', 299.00),
('ORD-004', 'U1001', '已发货', '京东物流 JD2026043000111', '派送中', '深圳市南山区科技园XX号', '2026-04-30 20:00前', 428.00),
('ORD-005', 'U1003', '已取消', NULL, NULL, '上海市浦东新区张江路ZZ号', NULL, 68.00);

-- 商品数据（5条，覆盖多品类）
INSERT INTO product_info (product_id, product_name, category, stock, price, description) VALUES
('PID-001', 'Java高级编程（第4版）', '图书', 156, 89.00, 'Java经典技术书籍，涵盖并发编程、JVM调优、网络编程'),
('PID-002', '罗技MX Master 3S鼠标', '电子', 42, 599.00, '静音点击，MagSpeed滚轮，多设备切换，续航70天'),
('PID-003', '优衣库纯棉T恤（白色）', '服装', 500, 79.00, '100%纯棉，透气舒适，多尺码可选'),
('PID-004', '三只松鼠坚果礼盒', '食品', 88, 128.00, '含夏威夷果、碧根果、核桃等8种坚果，760g'),
('PID-005', 'Spring AI实战指南', '图书', 0, 69.00, 'Spring AI框架入门到实战，含RAG/Tool Calling/Agent案例');

-- 售后工单数据（3条）
INSERT INTO aftersale_ticket (ticket_id, order_id, user_id, type, reason, status, resolution) VALUES
('TK-2026042801', 'ORD-002', 'U1001', '换货', '收到的T恤尺码不合适，M码换L码', '已完成', '已安排快递上门取件，新货已发出'),
('TK-2026042901', 'ORD-001', 'U1001', '投诉', '快递显示已发货2天但物流无更新', '处理中', '已联系顺丰客服核实，等待反馈'),
('TK-2026043001', 'ORD-004', 'U1001', '退货', '鼠标滚轮有问题，使用不顺滑', '待处理', NULL);

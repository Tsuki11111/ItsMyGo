-- 虚拟物流（M7.5）
--
-- 发货时生成一张运单：虚拟快递公司 / 快递员 / 轨迹；
-- 状态按时间推进，eta 就是最终送达时间（到点即 DELIVERED，delivered_at = eta）。
-- 真实项目这里会接承运商回调；本项目用确定性模拟。
CREATE TABLE shipments
(
    id             BIGINT        NOT NULL AUTO_INCREMENT,
    order_id       BIGINT        NOT NULL COMMENT '订单 ID（一单一运单）',
    tracking_no    VARCHAR(64)   NOT NULL DEFAULT '' COMMENT '快递单号',
    carrier        VARCHAR(32)   NOT NULL DEFAULT '' COMMENT '虚拟快递公司',
    courier_name   VARCHAR(32)   NOT NULL DEFAULT '' COMMENT '虚拟快递员',
    courier_phone  VARCHAR(20)   NOT NULL DEFAULT '' COMMENT '虚拟快递员电话',
    origin_name    VARCHAR(64)   NOT NULL DEFAULT '' COMMENT '虚拟发货仓',
    origin_lng     DECIMAL(10, 6) NOT NULL DEFAULT 0 COMMENT '发货仓经度',
    origin_lat     DECIMAL(10, 6) NOT NULL DEFAULT 0 COMMENT '发货仓纬度',
    status         VARCHAR(32)   NOT NULL COMMENT 'PICKED_UP / IN_TRANSIT / OUT_FOR_DELIVERY / DELIVERED / CANCELLED',
    shipped_at     DATETIME      NOT NULL COMMENT '发货时间',
    eta            DATETIME      NOT NULL COMMENT '预计送达 = 实际送达时间',
    delivered_at   DATETIME      NULL COMMENT '实际送达时间（到点写 eta）',
    created_at     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_order_id (order_id),
    KEY idx_status_eta (status, eta)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT '虚拟物流运单';

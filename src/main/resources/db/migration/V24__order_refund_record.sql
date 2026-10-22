-- 订单退款记录（M16）
--
-- 用户申请退款时记录原因与申请时间；管理员同意/驳回时记录处理时间。
-- 原因允许为空（智能体代申请、老接口未传），但管理端和用户端都会展示。
ALTER TABLE orders
    ADD COLUMN refund_reason     VARCHAR(255) NOT NULL DEFAULT '' COMMENT '退款原因（用户填写）' AFTER status_before_refund,
    ADD COLUMN refund_applied_at DATETIME NULL COMMENT '退款申请时间' AFTER refund_reason,
    ADD COLUMN refund_handled_at DATETIME NULL COMMENT '退款处理时间（同意/驳回）' AFTER refund_applied_at;

-- 老数据：REFUNDING/REFUNDED 订单至少补上申请时间；REFUNDED 再补处理时间。
-- 只能拿 updated_at 近似，无法恢复真实原因。
UPDATE orders
SET refund_applied_at = updated_at
WHERE status IN ('REFUNDING', 'REFUNDED') AND refund_applied_at IS NULL;

UPDATE orders
SET refund_handled_at = updated_at
WHERE status = 'REFUNDED' AND refund_handled_at IS NULL;

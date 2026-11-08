-- 站内通知旧订单链接回填（2026-11-08）
-- 早期订单通知把订单号写进 link（?keyword=<orderNo>），订单页只认 ?orderId=<id>，
-- 点击历史通知只能落到列表、不会打开详情。这里按订单号回填成订单主键。
UPDATE notifications n
JOIN orders o
  ON SUBSTRING_INDEX(SUBSTRING_INDEX(n.link, 'keyword=', -1), '&', 1) = o.order_no
SET n.link = REPLACE(n.link, CONCAT('keyword=', o.order_no), CONCAT('orderId=', o.id))
WHERE n.link LIKE '/admin/orders?keyword=%'
   OR n.link LIKE '/merchant/orders?keyword=%';

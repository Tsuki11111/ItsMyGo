-- 顾客 ↔ 商家在线聊天：一店一客一会话，消息只追加
CREATE TABLE conversations (
  id BIGINT NOT NULL AUTO_INCREMENT,
  customer_user_id BIGINT NOT NULL COMMENT '顾客用户 ID',
  merchant_id BIGINT NOT NULL COMMENT '店铺 ID',
  last_message_id BIGINT NULL COMMENT '最后一条消息 ID，空会话为 NULL',
  last_message_preview VARCHAR(200) NOT NULL DEFAULT '' COMMENT '会话列表预览',
  last_message_at DATETIME NULL COMMENT '最后消息时间，列表排序用',
  customer_unread INT NOT NULL DEFAULT 0 COMMENT '顾客未读数',
  merchant_unread INT NOT NULL DEFAULT 0 COMMENT '商家未读数',
  customer_last_read_message_id BIGINT NOT NULL DEFAULT 0 COMMENT '顾客已读指针，只前进',
  merchant_last_read_message_id BIGINT NOT NULL DEFAULT 0 COMMENT '商家已读指针，只前进',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_customer_merchant (customer_user_id, merchant_id),
  KEY idx_customer_activity (customer_user_id, last_message_at),
  KEY idx_merchant_activity (merchant_id, last_message_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='顾客-商家会话';

CREATE TABLE messages (
  id BIGINT NOT NULL AUTO_INCREMENT,
  conversation_id BIGINT NOT NULL COMMENT '会话 ID',
  sender_user_id BIGINT NOT NULL COMMENT '发送者用户 ID',
  sender_type VARCHAR(16) NOT NULL COMMENT 'CUSTOMER / MERCHANT，预留 AGENT',
  content_type VARCHAR(16) NOT NULL COMMENT 'TEXT / IMAGE / PRODUCT / ORDER',
  content VARCHAR(2000) NOT NULL DEFAULT '' COMMENT '文本、图片 URL 或卡片标题快照',
  card_data TEXT NULL COMMENT '商品/订单卡片 JSON 快照',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_conversation (conversation_id, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='聊天消息';

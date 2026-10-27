-- 商品问答（问大家）
--
-- 和 reviews 分开：reviews 是买家评分评价，计入商品平均分；
-- product_questions / product_answers 是公开问答，不计入评分。
-- 提问任何登录用户都可以；回答要求该用户买过这个商品（PAID/SHIPPED/COMPLETED）。

CREATE TABLE product_questions
(
    id           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '提问 ID',
    product_id   BIGINT       NOT NULL COMMENT '商品 ID',
    user_id      BIGINT       NOT NULL COMMENT '提问用户',
    content      VARCHAR(500) NOT NULL COMMENT '提问内容',
    answer_count INT          NOT NULL DEFAULT 0 COMMENT '可见回答数（冗余，回答时递增）',
    status       TINYINT      NOT NULL DEFAULT 1 COMMENT '1 展示 / 0 管理端隐藏',
    created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_product_status (product_id, status, id),
    KEY idx_user (user_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT '商品提问';

CREATE TABLE product_answers
(
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '回答 ID',
    question_id BIGINT       NOT NULL COMMENT '提问 ID',
    user_id     BIGINT       NOT NULL COMMENT '回答用户（必须买过该商品）',
    content     VARCHAR(500) NOT NULL COMMENT '回答内容',
    status      TINYINT      NOT NULL DEFAULT 1 COMMENT '1 展示 / 0 管理端隐藏',
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_question_status (question_id, status, id),
    KEY idx_user (user_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT '商品回答';

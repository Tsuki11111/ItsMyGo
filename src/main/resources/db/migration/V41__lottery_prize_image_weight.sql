-- 奖品增加实物图与中奖权重。
--
-- image：转盘上展示的奖品图；空串表示无图（前端用优惠券图标兜底）。
-- weight：加权抽奖的权重，越大越容易中；小奖（优惠券）配更高权重。
-- V40 的老数据由 DEFAULT 补齐，不需要回填。

ALTER TABLE lottery_prizes
    ADD COLUMN image VARCHAR(255) NOT NULL DEFAULT '' COMMENT '奖品实物图 URL；空串表示无图' AFTER name,
    ADD COLUMN weight INT NOT NULL DEFAULT 1 COMMENT '中奖权重，越大越容易中' AFTER level;

-- 活动平台化：抽奖不再是一张独立的「抽奖活动表」，而是活动的一种类型。
--
-- 1. 主表从 lottery_activities 改名为 activities；
-- 2. 增加 type / description / primary_color / cover_image；
-- 3. lottery_prizes / lottery_draw_records 的 activity_id 继续指向 activities.id，不需要改。
--
-- 老数据默认 type = LOTTERY，description / cover_image 为空，primary_color 用主题粉。

RENAME TABLE lottery_activities TO activities;

ALTER TABLE activities
    ADD COLUMN type VARCHAR(32) NOT NULL DEFAULT 'LOTTERY' COMMENT '活动类型：LOTTERY / 后续扩展' AFTER id,
    ADD COLUMN description VARCHAR(255) NOT NULL DEFAULT '' COMMENT '活动卡片副标题' AFTER name,
    ADD COLUMN primary_color VARCHAR(16) NOT NULL DEFAULT '#e8638f' COMMENT '活动主色调' AFTER description,
    ADD COLUMN cover_image VARCHAR(255) NOT NULL DEFAULT '' COMMENT '活动封面图 URL' AFTER primary_color,
    ADD KEY idx_type_start (type, start_at),
    COMMENT = '平台活动（抽奖等）';

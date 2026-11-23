-- 游客身份聊天室：建表/加列脚本（远程 MySQL 执行一次）
-- 注意：ADD COLUMN 不支持 IF NOT EXISTS，重复执行会报“Duplicate column”。
-- 若某列已存在（例如上一版已加过 nickname/openid/avatar），请删除对应行后再执行。

-- user 表新增游客身份相关列
ALTER TABLE `user`
    ADD COLUMN `nickname`      VARCHAR(64) NULL COMMENT '昵称',
    ADD COLUMN `anon_id`       VARCHAR(64) NULL COMMENT '游客匿名 ID（guest_id cookie 值）',
    ADD UNIQUE KEY `uk_anon_id` (`anon_id`),
    ADD COLUMN `avatar_color`  VARCHAR(16) NULL COMMENT '头像颜色',
    ADD COLUMN `ip`            VARCHAR(64) NULL COMMENT '最近登录 IP',
    ADD COLUMN `first_seen_at` DATETIME    NULL COMMENT '首次进入时间',
    ADD COLUMN `last_seen_at`  DATETIME    NULL COMMENT '最近进入时间';

-- 消息表（重建：按 anon_id 关联发送者，时间用毫秒时间戳；旧的 WeChat 测试数据会被清空）
DROP TABLE IF EXISTS `message`;
CREATE TABLE `message` (
    `id`           BIGINT      NOT NULL AUTO_INCREMENT,
    `sender_id`    VARCHAR(64) NOT NULL COMMENT '发送者 anon_id',
    `nickname`     VARCHAR(64) NOT NULL COMMENT '昵称快照',
    `avatar_color` VARCHAR(16) DEFAULT NULL COMMENT '头像颜色快照',
    `type`         VARCHAR(16) NOT NULL COMMENT 'text（系统消息不落库）',
    `content`      TEXT        NOT NULL,
    `create_time`  BIGINT      NOT NULL COMMENT '发送时间戳（毫秒）',
    PRIMARY KEY (`id`),
    KEY `idx_sender_id` (`sender_id`),
    KEY `idx_create_time` (`create_time`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

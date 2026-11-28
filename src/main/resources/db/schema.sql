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

-- 聊天室房间表（多聊天室）
CREATE TABLE IF NOT EXISTS `room` (
    `id`              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '房间主键',
    `code`            VARCHAR(16)  NOT NULL COMMENT '房间号（唯一，6位数字）',
    `name`            VARCHAR(64)  NOT NULL COMMENT '房间名称',
    `is_private`      TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '是否私密房：0公开 1私密',
    `password_hash`   VARCHAR(100) DEFAULT NULL COMMENT '私密房密码 BCrypt 哈希（公开房为空）',
    `creator_anon_id` VARCHAR(64)  DEFAULT NULL COMMENT '创建者 anon_id',
    `created_at`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_room_code` (`code`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '聊天室房间表';

-- 若已有新版 message 表仅需增量升级字段，可直接按需单独执行下面语句：
-- ALTER TABLE `message` ADD COLUMN `reply_to_id` BIGINT DEFAULT NULL COMMENT '引用的消息 message.id' AFTER `room_id`;
-- ALTER TABLE `message` ADD COLUMN `revoked` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否已撤回：0否 1是' AFTER `content`;

-- 消息表（重建：增加 room_id、reply_to_id 与 revoked，不设默认大厅，旧测试数据会清空）
DROP TABLE IF EXISTS `message`;
CREATE TABLE `message` (
    `id`           BIGINT      NOT NULL AUTO_INCREMENT,
    `room_id`      BIGINT      NOT NULL COMMENT '所属房间 room.id',
    `reply_to_id`  BIGINT      DEFAULT NULL COMMENT '引用的消息 message.id',
    `sender_id`    VARCHAR(64) NOT NULL COMMENT '发送者 anon_id',
    `nickname`     VARCHAR(64) NOT NULL COMMENT '昵称快照',
    `avatar_color` VARCHAR(16) DEFAULT NULL COMMENT '头像颜色快照',
    `type`         VARCHAR(16) NOT NULL COMMENT 'text（系统消息不落库）',
    `content`      TEXT        NOT NULL,
    `revoked`      TINYINT(1)  NOT NULL DEFAULT 0 COMMENT '是否已撤回：0否 1是',
    `create_time`  BIGINT      NOT NULL COMMENT '发送时间戳（毫秒）',
    PRIMARY KEY (`id`),
    KEY `idx_room_id_id` (`room_id`, `id`),
    KEY `idx_sender_id` (`sender_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

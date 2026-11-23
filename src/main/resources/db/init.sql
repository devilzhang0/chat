-- =============================================================
-- MyChat 全新部署初始化脚本（在目标服务器 MySQL 上执行一次）
-- 说明：这是「从零建库」的完整建表脚本；schema.sql 是给已有
--       user 表的旧库做「加列」迁移用的，二者二选一。
-- 数据库：默认 test（可自定义，需同步修改 application.yml）
-- =============================================================

-- 1. 用户表（游客身份：匿名 ID + 昵称 + 头像色 + IP）
CREATE TABLE IF NOT EXISTS `user` (
    `id`            BIGINT       NOT NULL COMMENT '主键（雪花 ID，由 MyBatis-Plus 生成）',
    `name`          VARCHAR(64)  DEFAULT NULL COMMENT '账号名（admin 演示登录用，游客为空）',
    `age`           INT          DEFAULT NULL,
    `email`         VARCHAR(128) DEFAULT NULL,
    `nickname`      VARCHAR(64)  DEFAULT NULL COMMENT '昵称',
    `anon_id`       VARCHAR(64)  DEFAULT NULL COMMENT '游客匿名 ID（guest_id cookie 值）',
    `avatar_color`  VARCHAR(16)  DEFAULT NULL COMMENT '头像颜色',
    `ip`            VARCHAR(64)  DEFAULT NULL COMMENT '最近登录 IP',
    `first_seen_at` DATETIME     DEFAULT NULL COMMENT '首次进入时间',
    `last_seen_at`  DATETIME     DEFAULT NULL COMMENT '最近进入时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_anon_id` (`anon_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '用户表';

-- 2. 消息表
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
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '消息表';

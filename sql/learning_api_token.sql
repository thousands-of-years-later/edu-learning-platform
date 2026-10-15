-- ----------------------------
-- API 访问令牌表 learning_api_token
--
-- 背景（重要）：后台用 Cookie 会话（Shiro）鉴权，而小程序 / H5 这类客户端
-- 拿不到也不该拿后台的 Cookie，所以 /api/v1/** 改用自己的 Token。
--
-- 为什么是「随机 UUID 存表」而不是 JWT：
--   1. 仓库的缓存是 Ehcache，它是 JVM 内的，多实例之间不共享。
--      若把登录态放缓存里，用户在 A 机器登录、下个请求被负载均衡打到 B 机器就会 401。
--      Token 落到 MySQL 天然多实例共享，也省掉一套 Redis 依赖。
--   2. 存表可以随时【吊销】单个 Token（退出登录、封号、改密码），
--      JWT 只能等它自己过期，服务端说了不算。
--
-- 为什么 Token 直接明文存：
--   UUID.randomUUID() 去掉横线是 122 位随机数，猜不出来；本表也不含其它敏感信息。
--   后续若要再加固，可改为只存 SHA-256 摘要、登录时返回明文（只动 Mapper 一处）。
--
-- 过期策略：
--   固定有效期（默认 30 天，见 application.yml 的 api.token.expireDays），不做滑动续期 ——
--   每次请求都回写一次数据库不划算；过期后客户端重新登录即可。
--
-- 本脚本可重复执行（幂等）。
--
-- 执行方式：
--   mysql --default-character-set=utf8mb4 -h43.142.9.214 -P3306 -uedu_dev_03 -p learning_platform < sql/learning_api_token.sql
--
-- 注意：本表由 Mapper 直接读写，不进 Ehcache，执行完【不需要重启应用】。
-- ----------------------------

SET NAMES utf8mb4;

create table if not exists learning_api_token (
  token_id    bigint(20)   not null auto_increment comment '主键',
  token       varchar(64)  not null                comment '访问令牌（UUID 去横线，不可猜）',
  user_id     bigint(20)   not null                comment '用户ID（逻辑关联 sys_user.user_id）',
  login_ip    varchar(128) default ''              comment '签发时的客户端IP（排查 Token 泄露用）',
  expire_time datetime     not null                comment '过期时间',
  create_time datetime     default null            comment '签发时间',
  primary key (token_id),
  unique key uk_learning_api_token (token),
  key idx_learning_api_token_user (user_id)
) engine=innodb auto_increment=100 comment = '学习平台API访问令牌表';

-- ----------------------------
-- 自检
-- ----------------------------
select table_name, table_comment from information_schema.tables
 where table_schema = database() and table_name = 'learning_api_token';
-- 期望：1 张表

select count(*) as current_rows from learning_api_token;
-- 期望：0

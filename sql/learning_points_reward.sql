-- ----------------------------
-- 积分获取：观看课程/帖子去重表 + 广告观看去重表
--
-- 规则（2026-11）：
--   1) 课程小节、帖子：每条内容每天只能得一次积分，每次 +5；
--      需累计观看/阅读满 30 秒才结算，看多久都不再累加；
--      课程 + 帖子两类合计每天最多 50 分（10 条内容）。
--   2) 广告：完整看完一条 +10，每天最多 3 条（30 分），不占 50 分额度。
--
-- 本脚本可重复执行（幂等）。
-- 执行方式：
--   mysql --default-character-set=utf8mb4 -h43.142.9.214 -P3306 -uedu_dev_03 -p learning_platform < sql/learning_points_reward.sql
-- ----------------------------

SET NAMES utf8mb4;

-- ----------------------------
-- 1、内容观看累计表：记录"某人某天在某条内容上累计看了多少秒、是否已发分"
--    target_type：post（帖子）/ courseVideo（课程小节）
-- ----------------------------
create table if not exists learning_points_watch
(
    id              bigint       not null auto_increment comment '主键',
    user_id         bigint       not null comment '用户ID',
    target_type     varchar(20)  not null comment '内容类型：post / courseVideo',
    target_id       bigint       not null comment '内容ID：帖子ID / 课程小节ID',
    award_date      date         not null comment '归属日期（东八区）',
    watched_seconds int          not null default 0 comment '当天累计观看秒数',
    awarded         tinyint      not null default 0 comment '是否已发分：0否 1是',
    points          int          not null default 0 comment '已发积分',
    create_time     datetime     null comment '创建时间',
    update_time     datetime     null comment '更新时间',
    primary key (id),
    unique key uk_user_target_date (user_id, target_type, target_id, award_date)
) engine = InnoDB default charset = utf8mb4 comment = '积分-内容观看累计与去重';

-- ----------------------------
-- 2、广告观看表：同一用户同一天同一条广告只发一次
-- ----------------------------
create table if not exists learning_ad_view
(
    id          bigint      not null auto_increment comment '主键',
    user_id     bigint      not null comment '用户ID',
    ad_id       varchar(64) not null comment '广告唯一标识（广告位/广告ID）',
    view_date   date        not null comment '观看日期（东八区）',
    seconds     int         not null default 0 comment '本次观看秒数',
    points      int         not null default 0 comment '发放积分',
    create_time datetime    null comment '创建时间',
    primary key (id),
    unique key uk_user_ad_date (user_id, ad_id, view_date)
) engine = InnoDB default charset = utf8mb4 comment = '积分-广告观看去重';

-- 自检
select table_name, table_comment
  from information_schema.tables
 where table_schema = database()
   and table_name in ('learning_points_watch', 'learning_ad_view');

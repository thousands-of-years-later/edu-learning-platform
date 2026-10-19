-- ----------------------------
-- 签到 / 积分账户 / 积分流水
--
-- 背景（设计稿 07_签到记录、08_任务中心、19_积分规则）：
--   每日签到一次，连续 1..7 天分别得 5/8/10/12/15/18/20 分，
--   第 8 天起从第 1 档循环；中断一天后连续天数从 1 重新开始。
--   页面上展示：连续签到天数、累计签到天数、积分余额（"我的积分/总积分"）、
--   最近签到列表（日期 + 本次得分）、当月签到日历。
--
-- 三个表的职责
--   learning_checkin         一天一条签到记录（唯一索引兜底「一天只能签一次」）
--   learning_points_account  积分账户（余额 + 累计获得），一个用户一行，懒创建
--   learning_points_log      积分流水（只增不改），签到 +N / 以后兑换 -N 都记一条
--
-- 【免广告权益不在这三张表里】：按约定它属于另一个板块，本模块只维护余额；
-- 兑换模块以后从同一个 balance 扣分，并写一条 type='exchange' 的流水。
--
-- 执行方式：
--   mysql --default-character-set=utf8mb4 -h43.142.9.214 -P3306 -uedu_dev_03 -p learning_platform < sql/learning_checkin.sql
--
-- 可重复执行（幂等）：create table if not exists。
-- ----------------------------

SET NAMES utf8mb4;

-- ----------------------------
-- 1、签到记录
-- ----------------------------
create table if not exists learning_checkin (
  checkin_id      bigint  not null auto_increment           comment '签到记录ID',
  user_id         bigint  not null                         comment '用户ID（sys_user.user_id）',
  sign_date       date    not null                         comment '签到日期（东八区自然日，一天一条）',
  points          int     not null                         comment '本次获得的积分（按当时连续天数取档，历史不被改写）',
  continuous_days int     not null                         comment '本次签到后的连续签到天数（断签从 1 重新开始）',
  create_time     datetime default null                    comment '创建时间',
  primary key (checkin_id),
  unique key uk_checkin_user_date (user_id, sign_date),
  key idx_checkin_user_date (user_id, sign_date)
) engine = innodb auto_increment = 1 default charset = utf8mb4 comment = '签到记录';

-- ----------------------------
-- 2、积分账户（余额）
-- ----------------------------
create table if not exists learning_points_account (
  user_id      bigint   not null                    comment '用户ID（sys_user.user_id），一个用户一行',
  balance      int      not null default 0          comment '积分余额（页面上的"我的积分/总积分"，兑换会扣减）',
  total_earned int      not null default 0          comment '历史累计获得（只增，备查用，不参与展示口径）',
  create_time  datetime default null                comment '创建时间',
  update_time  datetime default null                comment '更新时间',
  primary key (user_id)
) engine = innodb default charset = utf8mb4 comment = '积分账户（余额）';

-- ----------------------------
-- 3、积分流水（只增不改）
-- ----------------------------
create table if not exists learning_points_log (
  log_id        bigint       not null auto_increment  comment '流水ID',
  user_id       bigint       not null                 comment '用户ID（sys_user.user_id）',
  change_amount int          not null                 comment '变动值（签到为正、兑换为负）',
  balance       int          not null                 comment '变动后的余额快照（任意一条都能看出当时余额，不必重放全表）',
  type          varchar(20)  not null                 comment '类型：checkin 签到 / exchange 兑换 / adjust 手工调整',
  ref_id        bigint       default null             comment '关联业务ID（签到记录 checkin_id 等）',
  remark        varchar(200) default null             comment '备注',
  create_time   datetime     default null             comment '创建时间',
  primary key (log_id),
  key idx_points_log_user (user_id, log_id)
) engine = innodb auto_increment = 1 default charset = utf8mb4 comment = '积分流水';

-- ----------------------------
-- 自检
-- ----------------------------
select table_name, table_comment from information_schema.tables
 where table_schema = database()
   and table_name in ('learning_checkin', 'learning_points_account', 'learning_points_log');

-- 唯一索引存在性（期望 1 行：uk_checkin_user_date）
select index_name, non_unique from information_schema.statistics
 where table_schema = database() and table_name = 'learning_checkin' and non_unique = 0;

select (select count(*) from learning_checkin) as 签到记录,
       (select count(*) from learning_points_account) as 积分账户,
       (select count(*) from learning_points_log) as 积分流水;

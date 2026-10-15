-- ----------------------------
-- 用户关注表 learning_follow
--
-- 背景：
--   社区此前只有【内容】层面的关系（learning_interaction 的点赞/收藏），
--   没有【用户对用户】的关注关系，个人主页上的「关注 / 粉丝」数字只能是假的。
--   本表补上真正的关注数据。粉丝数/关注数一律由本表 count(*) 实时统计 ——
--   【不】往 sys_user 加 fans_count / follow_count 冗余列：sys_user 是若依核心表，
--   后台用户管理、登录、Token 校验都要读它，为一个模块加两列会让每处用户查询
--   都多一个可能漂移的字段；MVP 规模下 count(*) 走索引足够快，真到瓶颈再加也不迟。
--   也正因为没有任何冗余计数，本表【没有】对应的 _fix_count.sql 对账脚本。
--
-- 设计：
--   只做【用户关注用户】这一种关系，不做 learning_interaction / learning_comment 那种
--   target_type 多态表：目标只有用户一种，多态只会让唯一索引多一列、
--   每条 SQL 多一个恒定条件（与 learning_course_rating 只针对课程是同一取舍）。
--   取消关注是【硬删除】（直接删行），因此唯一索引 uk_user_follow 是安全的 ——
--   不存在 learning_category 那种「软删除占位导致关系永久不可用」的问题
--   （与 learning_interaction 头注释里论证过的取舍一致）。
--   唯一索引同时负责【防重复关注】与【并发安全】。
--   本表【没有 del_flag】；user_id / follow_user_id 都是逻辑外键，不加物理 FK。
--
-- 本脚本可重复执行（幂等）。
--
-- 执行方式：
--   mysql --default-character-set=utf8mb4 -h43.142.9.214 -P3306 -uedu_dev_03 -p learning_platform < sql/learning_follow.sql
-- ----------------------------

SET NAMES utf8mb4;

create table if not exists learning_follow (
  follow_id      bigint(20) not null auto_increment comment '关注ID',
  user_id        bigint(20) not null                comment '关注者用户ID（逻辑关联 sys_user.user_id）',
  follow_user_id bigint(20) not null                comment '被关注用户ID（逻辑关联 sys_user.user_id）',
  create_time    datetime   default null            comment '关注时间',
  primary key (follow_id),
  unique key uk_user_follow (user_id, follow_user_id),
  key idx_user_time (user_id, create_time),
  key idx_follow_user_time (follow_user_id, create_time)
) engine=innodb auto_increment=100 comment = '学习平台用户关注表';

-- 索引说明（三个一起看）：
--   uk_user_follow        防重复关注 + 并发安全；同时覆盖关注数 count(*) where user_id=?
--                         与 /status 批量查询 where user_id=? and follow_user_id in (...)。
--   idx_user_time         「我关注的人」列表 where user_id=? order by create_time desc
--                         走索引序，不 filesort —— uk 的列序里夹着 follow_user_id，顶不了排序的位置。
--   idx_follow_user_time  粉丝列表（follow_user_id 等值）与粉丝数同理。
--   这就是 learning_comment.idx_target (target_type, target_id, del_flag, comment_id) 的同一做法：
--   把列表的【等值列 + 排序列】放进一个索引。
--   create_time 与自增 follow_id 同增，「按关注时间倒序」与「按 follow_id 倒序」语义一致。

-- ----------------------------
-- 自检
-- ----------------------------
select table_name, table_comment from information_schema.tables
 where table_schema = database() and table_name = 'learning_follow';
-- 期望：1 张表

select column_name, column_type, column_comment from information_schema.columns
 where table_schema = database() and table_name = 'learning_follow'
 order by ordinal_position;
-- 期望：4 列（follow_id / user_id / follow_user_id / create_time）

select index_name, non_unique, column_name, seq_in_index from information_schema.statistics
 where table_schema = database() and table_name = 'learning_follow'
 order by index_name, seq_in_index;
-- 期望：7 行（PRIMARY 1 列；uk_user_follow 2 列；idx_user_time 2 列；idx_follow_user_time 2 列）

select count(*) as current_rows from learning_follow;
-- 期望：0

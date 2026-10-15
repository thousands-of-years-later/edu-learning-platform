-- ----------------------------
-- 课程评分表 learning_course_rating + learning_course 的评分冗余列
--
-- 背景：
--   learning_course 一直带着 like_count / favorite_count / comment_count 三个冗余计数，
--   但没有评分表也没有评分列。本脚本补上真正的评分数据：每个用户对每门课程最多一条评分
--   （1-5 的整数），learning_course 新增的 rating_count / rating_score 两列随之降级为
--   【由本表维护的冗余统计】。
--
-- 设计：
--   评分【只针对课程】，不做 learning_interaction / learning_comment 那种 target_type
--   多态表：目标只有课程一种，多态只会让唯一索引多一列、每条 SQL 多一个恒定条件。
--   课程评分挂 learning_course.course_id（课程级）。
--   本表【没有 del_flag】：评分没有删除语义（取消评分不在本阶段功能里），改分就是 update，
--   唯一索引 uk_course_user 因此是安全的 —— 这正是 learning_interaction 头注释里
--   「软删除 + 唯一索引会让已删除的记录永久占位」所论证的取舍。
--
-- 计数维护约定：
--   learning_course.rating_count / rating_score 只由后端评分模块维护，后台新增/编辑课程
--   时不写这两列（对应 Mapper 已排除）。接口返回的平均分/评分人数一律以评分表的
--   count(*) / sum(score) 为【唯一真相】，两列只供后台列表展示；
--   怀疑漂移时重跑 learning_course_rating_fix_count.sql 对账。
--   ⚠️ rating_score 存的是【评分总和】，不是平均分：平均分 = rating_score / rating_count。
--
-- 本脚本可重复执行（幂等）：加列用 information_schema 判断后再动态执行 DDL
-- （MySQL 8 不支持 add column if not exists，这是本仓库第一处 ALTER，特此说明）。
--
-- ⚠️ 必须在【部署新版应用之前】执行本脚本：CourseMapper.xml 会 select 这两列，
--    先发应用会报 Unknown column 'c.rating_count'。
--
-- 执行方式：
--   mysql --default-character-set=utf8mb4 -h43.142.9.214 -P3306 -uedu_dev_03 -p learning_platform < sql/learning_course_rating.sql
-- ----------------------------

SET NAMES utf8mb4;

-- ----------------------------
-- 1、给 learning_course 幂等加两列评分冗余统计（排在 comment_count 之后）
-- ----------------------------

set @ddl := (select if(count(*) = 0,
    'alter table learning_course add column rating_count int(11) default 0 comment ''评分人数'' after comment_count',
    'do 0')
  from information_schema.columns
 where table_schema = database() and table_name = 'learning_course' and column_name = 'rating_count');
prepare stmt from @ddl;
execute stmt;
deallocate prepare stmt;

set @ddl := (select if(count(*) = 0,
    'alter table learning_course add column rating_score int(11) default 0 comment ''评分总分（各用户评分之和，平均分=本列/rating_count）'' after rating_count',
    'do 0')
  from information_schema.columns
 where table_schema = database() and table_name = 'learning_course' and column_name = 'rating_score');
prepare stmt from @ddl;
execute stmt;
deallocate prepare stmt;

-- ----------------------------
-- 2、课程评分表
--    唯一索引 uk_course_user 同时负责【防重复评分】与【并发安全】，
--    与 learning_interaction.uk_user_target 同一思路。
--    列序 course_id 在前：按课程聚合（where course_id = ?）走最左前缀，不用再单开 idx_course；
--    现有查询都是 (course_id, user_id) 等值，也不缺 user 前导索引。
-- ----------------------------

create table if not exists learning_course_rating (
  rating_id   bigint(20)   not null auto_increment comment '评分ID',
  course_id   bigint(20)   not null                comment '课程ID（逻辑关联 learning_course.course_id）',
  user_id     bigint(20)   not null                comment '评分人用户ID（逻辑关联 sys_user.user_id）',
  score       tinyint(4)   not null                comment '评分（1-5的整数）',
  create_time datetime     default null            comment '首次评分时间',
  update_time datetime     default null            comment '最近一次修改时间（没改过为 null）',
  primary key (rating_id),
  unique key uk_course_user (course_id, user_id)
) engine=innodb auto_increment=100 comment = '学习平台课程评分表';

-- ----------------------------
-- 自检
-- ----------------------------
select column_name, column_type, column_comment from information_schema.columns
 where table_schema = database() and table_name = 'learning_course'
   and column_name in ('rating_count', 'rating_score')
 order by ordinal_position;
-- 期望：2 行（rating_count / rating_score）

select table_name, table_comment from information_schema.tables
 where table_schema = database() and table_name = 'learning_course_rating';
-- 期望：1 张表

select index_name, non_unique, column_name from information_schema.statistics
 where table_schema = database() and table_name = 'learning_course_rating'
 order by index_name, seq_in_index;
-- 期望：3 行（PRIMARY/rating_id、uk_course_user/course_id、uk_course_user/user_id）

select count(*) as current_rows from learning_course_rating;
-- 期望：0

-- ----------------------------
-- 计数列加固：NOT NULL DEFAULT 0 + CHECK (>= 0)
--
-- 背景（缺陷⑦）：
--   learning_post / learning_course / learning_short_video 的 like_count、favorite_count
--   是「点赞收藏」模块维护的冗余计数。应用层已经有 greatest(...,0) 兜底 + 影响行数判定，
--   正常运行不会出现负数，但数据库层没有任何约束，存在两个隐患：
--     1) 直接 update ... set like_count = -3 能写进去（没有 CHECK）；
--     2) 列本身允许 NULL，而 NULL 参与运算还是 NULL ——
--        like_count = like_count + 1 会把计数永久算成 NULL，前端显示就废了。
--   本脚本补上最后一道防线。
--
-- 本脚本做三件事：
--   1) 前置自检：打印负数 / NULL 的存量行（期望三行都是 0）
--   2) 6 个计数列改成 NOT NULL DEFAULT 0
--   3) 6 个计数列加 CHECK (col >= 0)（MySQL 8.0.16+；本环境服务端 8.0.46）
--   4) 尾部自检：约束已就位、负数行数为 0
--
-- 可重复执行（幂等）：MODIFY 重复执行无副作用；ADD CONSTRAINT 前先查 information_schema，
-- 已存在则执行 do 0 跳过 —— MySQL 没有 ADD CONSTRAINT IF NOT EXISTS。
--
-- 执行方式：
--   mysql --default-character-set=utf8mb4 -h43.142.9.214 -P3306 -uedu_dev_03 -p learning_platform < sql/learning_count_guard.sql
--
-- 注意：第 1 步自检如果不为 0，先跑 learning_interaction_fix_count.sql 对账；
--       带着负数执行第 3 步会直接失败（这是设计如此：先修数据，再上约束）。
-- ----------------------------

SET NAMES utf8mb4;

-- ----------------------------
-- 1、前置自检：负数 / NULL 的存量行（期望三行都是 0）
-- ----------------------------
select 'learning_post' as tbl, count(*) as bad_rows
  from learning_post
 where like_count < 0 or favorite_count < 0 or like_count is null or favorite_count is null
union all
select 'learning_course', count(*)
  from learning_course
 where like_count < 0 or favorite_count < 0 or like_count is null or favorite_count is null
union all
select 'learning_short_video', count(*)
  from learning_short_video
 where like_count < 0 or favorite_count < 0 or like_count is null or favorite_count is null;

-- ----------------------------
-- 2、NOT NULL DEFAULT 0（同一句里改两列；重复执行无副作用）
-- ----------------------------
alter table learning_post
  modify like_count int not null default 0,
  modify favorite_count int not null default 0;

alter table learning_course
  modify like_count int not null default 0,
  modify favorite_count int not null default 0;

alter table learning_short_video
  modify like_count int not null default 0,
  modify favorite_count int not null default 0;

-- ----------------------------
-- 3、CHECK (>= 0)：约束已存在则跳过（幂等）
-- ----------------------------
set @ddl = if((select count(*) from information_schema.table_constraints
                where constraint_schema = database() and table_name = 'learning_post'
                  and constraint_name = 'chk_post_like_count') = 0,
              'alter table learning_post add constraint chk_post_like_count check (like_count >= 0)', 'do 0');
prepare s from @ddl; execute s; deallocate prepare s;

set @ddl = if((select count(*) from information_schema.table_constraints
                where constraint_schema = database() and table_name = 'learning_post'
                  and constraint_name = 'chk_post_favorite_count') = 0,
              'alter table learning_post add constraint chk_post_favorite_count check (favorite_count >= 0)', 'do 0');
prepare s from @ddl; execute s; deallocate prepare s;

set @ddl = if((select count(*) from information_schema.table_constraints
                where constraint_schema = database() and table_name = 'learning_course'
                  and constraint_name = 'chk_course_like_count') = 0,
              'alter table learning_course add constraint chk_course_like_count check (like_count >= 0)', 'do 0');
prepare s from @ddl; execute s; deallocate prepare s;

set @ddl = if((select count(*) from information_schema.table_constraints
                where constraint_schema = database() and table_name = 'learning_course'
                  and constraint_name = 'chk_course_favorite_count') = 0,
              'alter table learning_course add constraint chk_course_favorite_count check (favorite_count >= 0)', 'do 0');
prepare s from @ddl; execute s; deallocate prepare s;

set @ddl = if((select count(*) from information_schema.table_constraints
                where constraint_schema = database() and table_name = 'learning_short_video'
                  and constraint_name = 'chk_video_like_count') = 0,
              'alter table learning_short_video add constraint chk_video_like_count check (like_count >= 0)', 'do 0');
prepare s from @ddl; execute s; deallocate prepare s;

set @ddl = if((select count(*) from information_schema.table_constraints
                where constraint_schema = database() and table_name = 'learning_short_video'
                  and constraint_name = 'chk_video_favorite_count') = 0,
              'alter table learning_short_video add constraint chk_video_favorite_count check (favorite_count >= 0)', 'do 0');
prepare s from @ddl; execute s; deallocate prepare s;

-- ----------------------------
-- 4、尾部自检
-- ----------------------------

-- 4.1 约束清单（期望 6 行）
select tc.table_name, cc.constraint_name, cc.check_clause
  from information_schema.check_constraints cc
  join information_schema.table_constraints tc
    on tc.constraint_name = cc.constraint_name and tc.table_schema = cc.constraint_schema
 where cc.constraint_schema = database()
   and cc.constraint_name like 'chk\_%\_count'
 order by tc.table_name, cc.constraint_name;

-- 4.2 负数行总数（期望 0）
select
  (select count(*) from learning_post        where like_count < 0 or favorite_count < 0) +
  (select count(*) from learning_course      where like_count < 0 or favorite_count < 0) +
  (select count(*) from learning_short_video where like_count < 0 or favorite_count < 0) as negative_rows;

-- 4.3 列定义（期望 6 行的 is_nullable = NO、column_default = 0）
select table_name, column_name, column_type, is_nullable, column_default
  from information_schema.columns
 where table_schema = database()
   and table_name in ('learning_post', 'learning_course', 'learning_short_video')
   and column_name in ('like_count', 'favorite_count')
 order by table_name, column_name;

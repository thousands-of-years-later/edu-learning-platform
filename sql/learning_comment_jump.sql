-- ----------------------------
-- 评论精准跳转：通知表增加关联评论ID
--
-- 背景：客户端从「通知列表」点进评论/回复时，需要定位到具体那条评论；
--       原来通知只有 target_type / target_id（内容），没有评论ID。
--
-- 改动：
--   learning_notification 增加 comment_id（可空）：
--     · 评论（type=3）、回复（type=5）通知写入对应评论ID；
--     · 点赞/收藏/关注通知保持 NULL（仍然只跳内容页）；
--     · 历史通知为 NULL，客户端按"跳内容页顶部"处理。
--   增加索引 idx_notification_comment，便于按评论反查。
--
-- 本脚本可重复执行（幂等）。
-- 执行：
--   mysql --default-character-set=utf8mb4 -h43.142.9.214 -P3306 -uedu_dev_03 -p learning_platform < sql/learning_comment_jump.sql
-- ----------------------------

SET NAMES utf8mb4;

-- 1、加列（存在则跳过）
set @col_exists := (
    select count(*)
      from information_schema.columns
     where table_schema = database()
       and table_name = 'learning_notification'
       and column_name = 'comment_id'
);
set @ddl := if(@col_exists = 0,
    'alter table learning_notification add column comment_id bigint null comment ''关联评论ID（评论/回复通知用；历史通知为空）'' after target_id',
    'select 1');
prepare stmt from @ddl;
execute stmt;
deallocate prepare stmt;

-- 2、加索引（存在则跳过）
set @idx_exists := (
    select count(*)
      from information_schema.statistics
     where table_schema = database()
       and table_name = 'learning_notification'
       and index_name = 'idx_notification_comment'
);
set @ddl2 := if(@idx_exists = 0,
    'create index idx_notification_comment on learning_notification (comment_id)',
    'select 1');
prepare stmt2 from @ddl2;
execute stmt2;
deallocate prepare stmt2;

-- 自检
select column_name, column_type, is_nullable
  from information_schema.columns
 where table_schema = database()
   and table_name = 'learning_notification'
   and column_name in ('target_id', 'comment_id');

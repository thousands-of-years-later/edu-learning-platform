-- ----------------------------
-- 社区帖子 阅读数（learning_post.view_count）
--
-- 背景：
--   分类页 / 社区信息流的文章列表需要展示阅读数，但 learning_post 只有
--   like_count / favorite_count / comment_count，没有阅读计数列；
--   短视频表已有 view_count，文章与它保持同一口径。
--
-- 口径：
--   · 首次阅读 +1：观看历史上报（POST /api/v1/history/report，targetType=post）
--     首次插入 learning_watch_history 时累加；重复上报不重复计数。
--   · 同一用户多次打开只算一次（uk_history_user_target 唯一索引保证）；
--     用户删除/清空该文章的历史后再看，会重新按首次计算（与短视频一致）。
--   · 不在 GET /api/v1/post/info 时累加：GET 不产生写副作用，也避免刷新/预取刷高。
--   · 逻辑删除的帖子保留阅读数（与短视频 view_count 一致；列表只查 del_flag='0'）。
--
-- 前置条件：已执行 sql/learning_post.sql。
-- 本脚本可重复执行（幂等）：加列前查 information_schema，已存在则 do 0
-- （MySQL 8 不支持 add column if not exists）。
--
-- ⚠️ 必须在【部署新版应用之前】执行本脚本：
--    MyPostMapper.xml 的 selectPublishedList 会 select p.view_count，
--    先发应用会报 Unknown column 'p.view_count'。
--
-- 回填决策：不回填。learning_watch_history 是用户可以删除/清空的个人数据，
--   用它回填只能算出「还留着历史记录的用户数」，不是真实阅读数；
--   且本脚本可重复执行，回填语句重跑会覆盖上线后的增量。
--
-- 执行方式：
--   mysql --default-character-set=utf8mb4 -h43.142.9.214 -P3306 -uedu_dev_03 -p learning_platform < sql/learning_post_view_count.sql
-- ----------------------------

SET NAMES utf8mb4;

-- ----------------------------
-- 1、learning_post 幂等加阅读数
--    不写 after：列尾追加可走 MySQL 8 的 INSTANT DDL，减少锁表/重建风险。
-- ----------------------------

set @ddl := (select if(count(*) = 0,
    'alter table learning_post add column view_count int(11) not null default 0 comment ''阅读数（首次阅读时 +1，由观看历史维护）''',
    'do 0')
  from information_schema.columns
 where table_schema = database() and table_name = 'learning_post' and column_name = 'view_count');
prepare stmt from @ddl;
execute stmt;
deallocate prepare stmt;

-- 索引决策：view_count 只做展示，没有按它过滤/排序，不建索引。
-- 约束决策：只增不减，不存在负数/NULL 风险，不额外加 CHECK。

-- ----------------------------
-- 2、自检
-- ----------------------------

select column_name, column_type, is_nullable, column_default, column_comment
  from information_schema.columns
 where table_schema = database() and table_name = 'learning_post'
   and column_name = 'view_count';
-- 期望：1 行（int，is_nullable = NO，column_default = 0，注释含「阅读数」）

select count(*) as null_rows from learning_post where view_count is null;
-- 期望：0

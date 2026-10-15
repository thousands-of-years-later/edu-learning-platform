-- ----------------------------
-- 评论 二级回复（扁平两级）
--
-- 背景：
--   learning_comment 原来只有一级评论。本次加「回复评论」：所有回复（无论回复一级评论
--   还是回复另一条回复）都挂在【同一条一级评论】下（扁平两级，微信/微博形态），不再往下嵌套；
--   每条回复额外记录【被回复人】，客户端据此渲染「B 回复 A：xxx」。
--
-- 设计：
--   parent_id     bigint default null：一级评论为 NULL；二级评论存所属【一级】评论的 comment_id。
--                 【注意】存的是「一级评论ID」，不是直接父级 —— 回复二级评论时，
--                 服务层会把根上溯到它所属的一级评论再落库（见 CommentServiceImpl）。
--                 因此 where parent_id = <一级评论ID> 一次就能取回它的全部回复，不用递归。
--   reply_user_id bigint default null：被回复人。由服务端从被回复评论的作者推导，
--                 【不接受前端传入】—— 否则任何人都能伪造「xxx 回复了你」的通知。
--                 仅二级评论有值。
--   索引 idx_parent (parent_id, del_flag, comment_id)：与 idx_target 同一形态
--   （等值列 + 过滤列 + 排序列），正好覆盖「取某根的全部回复 + 按 comment_id 正序」。
--   不复用 idx_target（其前导列是 target_type）。idx_target 也不改：一级评论列表
--   (target_type, target_id, del_flag + order by comment_id desc) 仍走它，
--   多出的 parent_id is null 回表过滤，本量级可接受。
--
-- 计数口径（重要）：
--   接口返回的 count 与内容表 comment_count 一律【包含二级评论】（countByTarget 数全部
--   del_flag='0' 行，不受本次改动影响）；只有分页 TableDataInfo.total 是【一级评论数】
--   （selectCommentList 里写死了 parent_id is null），否则客户端按 total 算页数会翻出空页。
--   learning_comment_fix_count.sql 口径一致，【不用改】。
--
-- 删除语义：
--   一级评论被其作者删除时，服务层【级联软删】它名下的全部二级评论，计数按实际删除行数减少。
--   理由：列表只展示 del_flag='0' 的一级评论，父被删后子永远无法展示，
--   而 countByTarget 又会把它们算进去 —— 展示数与列表能翻到的行数会永久对不上。
--
--   内容（帖子/课程/短视频）被删时，community/course/video 三个模块的级联软删语句按
--   (target_type, target_id) 过滤，【天然覆盖二级评论】，这三处无需改动。
--
-- 通知：回复会通知被回复人（learning_notification.type 新增 5回复评论；被回复人同时是
--   内容作者时只发一条，见 NotificationServiceImpl.notifyReply）。
--   本次不建 sys_notification_type 字典 —— 与 learning_notification.sql 头注释的既有决定一致
--   （等真加了后台通知列表页再补）。
--
-- 本脚本可重复执行（幂等）：加列/加索引先用 information_schema 判断再动态执行 DDL
--   （MySQL 8 不支持 add column / create index if not exists，沿用 learning_course_rating.sql 写法）。
--
-- ⚠️ 必须【先执行本脚本、再部署新版应用】：CommentMapper.xml 会 select c.parent_id /
--    c.reply_user_id，应用先上会报 Unknown column。存量行不用回填：默认 NULL 正是一级评论。
--
-- 执行方式：
--   mysql --default-character-set=utf8mb4 -h43.142.9.214 -P3306 -uedu_dev_03 -p learning_platform < sql/learning_comment_reply.sql
-- ----------------------------

SET NAMES utf8mb4;

-- ----------------------------
-- 1、learning_comment 幂等加两列（parent_id 排在 target_id 之后，reply_user_id 紧随其后）
-- ----------------------------

set @ddl := (select if(count(*) = 0,
    'alter table learning_comment add column parent_id bigint(20) default null comment ''父评论ID（一级评论为NULL；二级评论存所属一级评论的comment_id，不是直接父级）'' after target_id',
    'do 0')
  from information_schema.columns
 where table_schema = database() and table_name = 'learning_comment' and column_name = 'parent_id');
prepare stmt from @ddl;
execute stmt;
deallocate prepare stmt;

set @ddl := (select if(count(*) = 0,
    'alter table learning_comment add column reply_user_id bigint(20) default null comment ''被回复人用户ID（仅二级评论有值；服务端从被回复评论的作者推导，不接受前端传入）'' after parent_id',
    'do 0')
  from information_schema.columns
 where table_schema = database() and table_name = 'learning_comment' and column_name = 'reply_user_id');
prepare stmt from @ddl;
execute stmt;
deallocate prepare stmt;

-- ----------------------------
-- 2、索引 idx_parent
--    seq：parent_id（等值）-> del_flag（等值）-> comment_id（排序）
--    正好覆盖「where parent_id = ? and del_flag = '0' order by comment_id asc」，免 filesort
-- ----------------------------

set @ddl := (select if(count(*) = 0,
    'alter table learning_comment add index idx_parent (parent_id, del_flag, comment_id)',
    'do 0')
  from information_schema.statistics
 where table_schema = database() and table_name = 'learning_comment' and index_name = 'idx_parent');
prepare stmt from @ddl;
execute stmt;
deallocate prepare stmt;

-- ----------------------------
-- 3、learning_notification.type 注释补上 5回复评论（信息性修改，不动数据；comment 已含「5回复」时跳过）
--    注：判断条件必须写在 where 里而不是 select 的 if() 里——select 里同时出现 count(*) 与
--    column_comment 会被 MySQL 8 默认的 only_full_group_by 拒绝（ERROR 1140），
--    见 learning_post_audit.sql 的同一处说明
-- ----------------------------

set @ddl := (select if(count(*) = 0, 'do 0',
    'alter table learning_notification modify column type char(1) not null comment ''通知类型（1点赞 2收藏 3评论 4关注 5回复评论）''')
  from information_schema.columns
 where table_schema = database() and table_name = 'learning_notification' and column_name = 'type'
   and ifnull(column_comment, '') not like '%5回复%');
prepare stmt from @ddl;
execute stmt;
deallocate prepare stmt;

-- ----------------------------
-- 自检
-- ----------------------------

select column_name, column_type, column_comment from information_schema.columns
 where table_schema = database() and table_name = 'learning_comment'
 order by ordinal_position;
-- 期望：10 列（原 8 列 + parent_id + reply_user_id，parent_id/reply_user_id 排在 target_id 之后）

select index_name, non_unique, column_name, seq_in_index from information_schema.statistics
 where table_schema = database() and table_name = 'learning_comment'
 order by index_name, seq_in_index;
-- 期望：10 行（idx_parent 3 + idx_target 4 + idx_user 2 + PRIMARY 1）

select column_comment from information_schema.columns
 where table_schema = database() and table_name = 'learning_notification' and column_name = 'type';
-- 期望：注释含「5回复评论」

select count(*) as existing_replies from learning_comment where parent_id is not null;
-- 期望：0（存量都是一级评论，无需回填）

-- 数据自检：下面两条在【新版应用上线运行后】才有意义，上线前应恒为 0

select count(*) as bad_level2 from learning_comment where parent_id is not null and reply_user_id is null;
-- 期望：0（二级评论必须带被回复人）

select count(*) as orphan_replies from learning_comment c
  join learning_comment p on p.comment_id = c.parent_id
 where c.del_flag = '0' and p.del_flag = '2';
-- 期望：0（父被删时子会级联软删，不应出现「父已删、子还活着」）
-- 注：删一级评论与「回复其名下二级」存在毫秒级并发窗口，可能残留极少量孤儿（见计划的风险 5）；
--     真出现了就是这里的查询能发现，按精确主键手工补软删即可。

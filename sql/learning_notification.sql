-- ----------------------------
-- 用户通知表 learning_notification
--
-- 背景：
--   点赞 / 收藏 / 评论 / 关注发生后，被触发者（内容作者、被关注者）此前【收不到任何提示】，
--   只能靠自己去翻别人的主页。本表是【用户对用户】的通知收件箱。
--   注意：与后台的 sys_notice / sys_notice_read（管理员发公告、通知公告菜单）是两套东西，
--   互不关联，也【不】复用它们的表结构。
--
-- 设计：
--   一行 = 一条通知：user_id 是【接收者（被触发者）】，from_user_id 是【触发者】。
--   type：1点赞 2收藏 3评论 4关注 —— 其中 1/2 与 learning_interaction.type 的编码【刻意相同】，
--   点赞/收藏通知直接透传 Interaction 的 type，不用做翻译。
--   target_type（1帖子 2课程 3短视频）沿用 sys_interaction_target 字典的编码。
--   关注类没有内容目标，target_type / target_id 为 NULL（被关注人就是 from_user_id）。
--   content 只在【评论类】有值，存评论原文（trim 后、最长500字，与 learning_comment.content 一致），
--   这样列表接口不用回查评论表；纯文本，渲染端只能用文本节点（禁止 innerHTML），与评论同一约定。
--
--   课程（target_type=2）【永远不会产生通知】：learning_course 没有 user_id 归属人列
--   （只有 create_by 管理员账号名），没有「被触发者」可言 —— 服务层对课程直接跳过。
--   自我操作（自己赞自己/自评自论）同样不产生通知。
--
--   已读状态 = is_read（0未读 1已读）+ read_time，直接写在通知行上，【不建】已读明细表
--   （与 sys_notice_read 那种「一条公告多人已读」的模型不同：本表一行本来就只属于一个人）。
--   取消点赞/收藏/关注【不删除】已产生的通知行，也不提供删除接口 —— 代价是
--   「赞 → 取消 → 再赞」会产生两条通知，MVP 接受。
--   本表没有 del_flag（无删除），也没有历史归档/清理任务，行数只有增长，
--   真到瓶颈再加归档，不在 MVP 里预先设计。
--
--   索引说明（两个一起看）：
--   idx_user_time       「全部」列表 where user_id=? order by create_time desc 的
--                       【等值列 + 排序列】，走索引序不 filesort
--                       （与 learning_follow.idx_user_time 同一做法）。
--   idx_user_read_time  未读列表？isRead=0 / 未读数 count(*) / 「全部已读」update 的
--                       (user_id, is_read) 等值；第三列 create_time 让未读列表同样免 filesort。
--                       不能只建 (user_id, is_read)：那样「全部」列表按 create_time 排序会 filesort；
--                       反过来只建 (user_id, create_time)：未读数要回表过滤，不便宜。
--   InnoDB 二级索引末尾隐式带主键 notification_id，create_time 同秒并列时翻页次序也稳定。
--
--   本脚本可重复执行（幂等）。
--
-- 执行方式：
--   mysql --default-character-set=utf8mb4 -h43.142.9.214 -P3306 -uedu_dev_03 -p learning_platform < sql/learning_notification.sql
-- ----------------------------

SET NAMES utf8mb4;

create table if not exists learning_notification (
  notification_id bigint(20)   not null auto_increment comment '通知ID',
  user_id         bigint(20)   not null                comment '接收者用户ID（被触发者，逻辑关联 sys_user.user_id）',
  from_user_id    bigint(20)   not null                comment '触发者用户ID（点头像的那位，逻辑关联 sys_user.user_id）',
  type            char(1)      not null                comment '通知类型（1点赞 2收藏 3评论 4关注）',
  target_type     char(1)      default null            comment '目标类型（1帖子 3短视频；关注类为NULL；课程永不产生通知）',
  target_id       bigint(20)   default null            comment '目标ID（关注类为NULL）',
  content         varchar(500) default null            comment '评论摘要（仅评论类有值，与 learning_comment.content 一致）',
  is_read         char(1)      not null default '0'    comment '读取状态（0未读 1已读）',
  read_time       datetime     default null            comment '读取时间（未读为NULL）',
  create_time     datetime     default null            comment '产生时间',
  primary key (notification_id),
  key idx_user_time (user_id, create_time),
  key idx_user_read_time (user_id, is_read, create_time)
) engine=innodb auto_increment=100 comment = '学习平台用户通知表';

-- 关于字典：本表【不注册】新的字典类型。理由：
--   1) 通知类型是固定 4 个值的内部常量（Notification.TYPE_*），只在代码/客户端渲染里用，
--      目前没有任何后台页面通过字典读它（learning_follow.sql 同样没注册字典）；
--   2) target_type 的含义与 sys_interaction_target 完全相同，要展示直接复用既有字典，
--      不另建一份近似重复的（与 learning_comment.sql 的取舍一致）。
--   将来若加了后台通知列表页，再幂等补一段 sys_notification_type 的 insert 即可。

-- ----------------------------
-- 自检
-- ----------------------------
select table_name, table_comment from information_schema.tables
 where table_schema = database() and table_name = 'learning_notification';
-- 期望：1 张表

select column_name, column_type, column_comment from information_schema.columns
 where table_schema = database() and table_name = 'learning_notification'
 order by ordinal_position;
-- 期望：10 列（notification_id / user_id / from_user_id / type / target_type / target_id /
--              content / is_read / read_time / create_time）

select index_name, non_unique, column_name, seq_in_index from information_schema.statistics
 where table_schema = database() and table_name = 'learning_notification'
 order by index_name, seq_in_index;
-- 期望：6 行（PRIMARY 1 列；idx_user_time 2 列；idx_user_read_time 3 列）

select count(*) as current_rows from learning_notification;
-- 期望：0

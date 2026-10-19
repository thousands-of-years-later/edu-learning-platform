-- ----------------------------
-- 笔记表 learning_note
--
-- 背景：
--   设计稿 24_笔记 / 25_笔记详情：用户看课程（课程下有多个视频/小节）时，可以在
--   【每个小节】下面记笔记；社区里刷到的帖子也能记笔记。个人中心可以按
--   「全部 / 文章笔记 / 视频笔记」查看、搜索、编辑、删除，点一条笔记能跳回
--   记笔记的地方。
--
-- 两件事先约定清楚：
--   1) target_type 用【字符串】post / video，不用数字。
--      Interaction / Comment 里 "2" 表示「课程」，而笔记的 video 指的是课程下的
--      一个【小节】，照搬数字会让不同表里同一个值含义不同；
--      learning_category.category_type 已经是 course/article 这种字符串，有先例。
--      video 挂的是 learning_course_video.video_id，【不是】learning_short_video。
--      短视频不做笔记功能。
--   2) 内容被逻辑删除后，笔记【保留】—— 笔记是用户自己的资产，接口通过
--      sourceDelFlag / sourceDeleted 告诉客户端显示「来源已删除」。
--      这是产品设计，不是孤儿数据：learning_orphan_cleanup.sql 不要清理本表。
--
-- 执行方式：
--   mysql --default-character-set=utf8mb4 -h43.142.9.214 -P3306 -uedu_dev_03 -p learning_platform < sql/learning_note.sql
--
-- 可重复执行（幂等）：create table if not exists。
-- ----------------------------

SET NAMES utf8mb4;

create table if not exists learning_note (
  note_id      bigint       not null auto_increment          comment '笔记ID',
  user_id      bigint       not null                        comment '作者用户ID（sys_user.user_id，一律取 API 上下文）',
  target_type  varchar(20)  not null                        comment '挂载类型：post=帖子（文章笔记） video=课程小节（视频笔记，挂 learning_course_video.video_id）',
  target_id    bigint       not null                        comment '挂载目标ID：post_id 或 课程小节的 video_id',
  note_title   varchar(20)  not null                        comment '标题（纯文本，≤20字）',
  note_content varchar(800) not null                        comment '正文（纯文本，≤800字）',
  progress     int          default null                    comment '视频笔记：记笔记时的播放位置(秒)；文章笔记为 NULL。仅展示用，跳转时从头播放',
  del_flag     char(1)      default '0'                     comment '删除标志（0存在 2删除）',
  create_time  datetime     default null                    comment '创建时间',
  update_time  datetime     default null                    comment '更新时间（列表按它倒序 = 最近编辑在前）',
  primary key (note_id),
  key idx_note_user (user_id, del_flag, update_time),
  key idx_note_target (target_type, target_id, del_flag)
) engine = innodb auto_increment = 1 default charset = utf8mb4 comment = '用户笔记（课程小节 / 帖子）';

-- ----------------------------
-- 自检
-- ----------------------------
-- 1.1 表结构与列注释
select table_name, table_comment from information_schema.tables
 where table_schema = database() and table_name = 'learning_note';

select column_name, column_type, is_nullable, column_default, column_comment
  from information_schema.columns
 where table_schema = database() and table_name = 'learning_note'
 order by ordinal_position;

-- 1.2 索引（期望 3 个：PRIMARY / idx_note_user / idx_note_target）
show index from learning_note;

-- 1.3 当前笔记数
select count(*) as note_rows from learning_note;

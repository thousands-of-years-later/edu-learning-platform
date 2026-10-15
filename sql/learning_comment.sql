-- ----------------------------
-- 评论表 learning_comment
--
-- 背景：
--   learning_post / learning_course / learning_short_video 三张内容表一直带着
--   comment_count 列，但在本脚本之前没有任何评论表，该列只能靠后台编辑表单手填，
--   是个假计数。本表补上真正的评论数据，comment_count 随之降级为
--   【由本表维护的冗余统计】。
--
-- 设计：
--   一张表管三种内容（1帖子 2课程 3短视频），target_type 编码与 learning_interaction
--   【完全相同】，改动时要一起改。
--   课程评论挂 learning_course.course_id（课程级），【不是】learning_course_video.video_id
--   （课程内的章节视频没有 comment_count 列）。
--   逻辑删除（del_flag 0存在 2删除）：本表没有唯一索引要维护，用户本来就可以重复发同样的话，
--   因此不存在 learning_interaction 那种「硬删除才能建唯一索引」的约束；
--   内容表一律用 del_flag，删除后保留原文也便于日后审核。
--
-- 计数维护约定：
--   learning_post / learning_course / learning_short_video 的 comment_count
--   一律【由后端评论模块维护】，后台新增/编辑内容时不再写入该字段（对应 Mapper 已去掉这一列）。
--   接口返回的评论数以 count(*) 为准，comment_count 只供后台列表展示；
--   怀疑漂移时重跑 learning_comment_fix_count.sql 对账。
--
-- 内容字段约定：
--   content 是【纯文本】，原样存 trim 后的用户输入，不做转义。
--   渲染端只能以文本节点输出（th:text / 小程序 <text> / textContent），禁止 innerHTML。
--
-- 本脚本可重复执行（幂等）。
--
-- 执行方式：
--   mysql --default-character-set=utf8mb4 -h43.142.9.214 -P3306 -uedu_dev_03 -p learning_platform < sql/learning_comment.sql
-- ----------------------------

SET NAMES utf8mb4;

create table if not exists learning_comment (
  comment_id  bigint(20)   not null auto_increment comment '评论ID',
  user_id     bigint(20)   not null                comment '评论人用户ID（逻辑关联 sys_user.user_id）',
  target_type char(1)      not null                comment '目标类型（1帖子 2课程 3短视频）',
  target_id   bigint(20)   not null                comment '目标ID',
  content     varchar(500) not null                comment '评论内容（纯文本，最长500字）',
  del_flag    char(1)      default '0'             comment '删除标志（0代表存在 2代表删除）',
  create_time datetime     default null            comment '评论时间',
  update_time datetime     default null            comment '更新时间（逻辑删除时写入）',
  primary key (comment_id),
  key idx_target (target_type, target_id, del_flag, comment_id),
  key idx_user (user_id, del_flag)
) engine=innodb auto_increment=100 comment = '学习平台评论表';

-- 目标类型字典沿用已有的 sys_interaction_target（含义同为「1帖子 2课程 3短视频」），
-- 不另建一份近似重复的字典。

-- ----------------------------
-- 自检
-- ----------------------------
select table_name, table_comment from information_schema.tables
 where table_schema = database() and table_name = 'learning_comment';
-- 期望：1 张表

select column_name, column_type, column_comment from information_schema.columns
 where table_schema = database() and table_name = 'learning_comment'
 order by ordinal_position;
-- 期望：8 列（comment_id / user_id / target_type / target_id / content / del_flag / create_time / update_time）

select count(*) as current_rows from learning_comment;
-- 期望：0

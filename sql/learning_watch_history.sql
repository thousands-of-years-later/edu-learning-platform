-- ----------------------------
-- 观看历史 learning_watch_history
--
-- 设计稿 21_观看历史：列表按最近观看时间倒序，每条「封面 + 标题 + 时间 · 看到 X 分 Y 秒」，
-- 右上角「清空」。三种内容混在一条时间线里：
--   shortVideo = 短视频（learning_short_video.video_id）
--   course     = 课程（learning_course.course_id）
--   post       = 文章/帖子（learning_post.post_id）
--
-- 两个口径（产品确认过）：
--   1) 进度【跟随最新】：重看时直接覆盖 position，不取最大值 —— 历史里的"看到 X"永远等于
--      最近一次播放的位置，断点续播也从这里接着播。
--   2) 课程粒度【按课程】：一门课只留一条，video_id 记住"上次看到哪个小节"，
--      position 是【该小节内】的秒数；点历史直接进那个小节并跳位置。
--      短视频和文章天然一条一个内容。文章没有进度，只记"点开过 + 时间"。
--
-- 计数联动：首次观看上报会 +1 内容计数 —— 短视频 learning_short_video.view_count（播放数），
--   文章 learning_post.view_count（阅读数）。重复上报不重复计数；课程没有 view_count 列。
--
-- 唯一索引 (user_id, target_type, target_id)：同一条内容只留一行，重看 = 更新，
-- 同时兜住并发上报（两个请求同时上报只会有一个插入成功，另一个走更新）。
--
-- 执行方式：
--   mysql --default-character-set=utf8mb4 -h43.142.9.214 -P3306 -uedu_dev_03 -p learning_platform < sql/learning_watch_history.sql
--
-- 可重复执行（幂等）：create table if not exists。
-- ----------------------------

SET NAMES utf8mb4;

create table if not exists learning_watch_history (
  history_id     bigint      not null auto_increment comment '历史ID',
  user_id        bigint      not null               comment '用户ID（sys_user.user_id，从 Token 取）',
  target_type    varchar(20) not null                comment '内容类型：shortVideo 短视频 / course 课程 / post 文章',
  target_id      bigint      not null               comment '内容ID：短视频ID / 课程ID / 帖子ID',
  video_id       bigint      default null           comment '课程：上次观看的小节ID（断点续播定位用）',
  position       int         default null           comment '视频：最近一次看到第几秒（重看直接覆盖）',
  duration       int         default null           comment '视频：总时长(秒)；短视频取内容表的，课程小节由客户端上报',
  last_view_time datetime    not null               comment '最近观看时间（列表按它倒序）',
  create_time    datetime    default null           comment '首次观看时间',
  primary key (history_id),
  unique key uk_history_user_target (user_id, target_type, target_id),
  key idx_history_user_time (user_id, last_view_time)
) engine = innodb auto_increment = 1 default charset = utf8mb4 comment = '观看历史（短视频/课程/文章）';

-- ----------------------------
-- 自检
-- ----------------------------
select table_name, table_comment from information_schema.tables
 where table_schema = database() and table_name = 'learning_watch_history';

select column_name, column_type, column_comment from information_schema.columns
 where table_schema = database() and table_name = 'learning_watch_history'
 order by ordinal_position;

-- 唯一索引（期望 1 行：uk_history_user_target）
select index_name, non_unique from information_schema.statistics
 where table_schema = database() and table_name = 'learning_watch_history' and non_unique = 0;

select count(*) as history_rows from learning_watch_history;

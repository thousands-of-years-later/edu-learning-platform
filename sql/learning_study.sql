-- ----------------------------
-- 学习数据 learning_study_record / learning_course_progress
--
-- 对应设计稿 20_学习数据：今日学习（分钟）、连续学习（天）、本周学习时长（周一到周日柱状图）、
--                        已完成课程 / 笔记 / 待办 三个数字。
--
-- 为什么要两张表：
--   1. learning_study_record —— 按【天】汇总学习时长（user_id + study_date 一行）。
--      今日分钟、本周柱状图、连续学习天数、累计时长全部从这张表算，一条 SQL 就够，不用扫播放明细。
--   2. learning_course_progress —— 按【课程小节】记学习进度（user_id + video_id 一行）。
--      "已完成课程" = 该课程下所有小节都 finished=1；"待办" = 已经开始但还没学完的小节数。
--
-- 时长口径（产品确认）：
--   · 客户端播放时（暂停/退出/每 30 秒）上报本次新增学习秒数，服务端累加到【当天】；
--   · 同一小节重复上报只累计时长，不重复算完成；finished 一旦为 1 不会被后续上报改回 0；
--   · 单次上报 seconds 限制 1~3600 秒，防止客户端异常上报把时长刷爆；
--   · 日期与"本周"统一按 Asia/Shanghai 计算（服务器可能是 UTC）。
--
-- 执行方式：
--   mysql --default-character-set=utf8mb4 -h43.142.9.214 -P3306 -uedu_dev_03 -p learning_platform < sql/learning_study.sql
--
-- 可重复执行（幂等）。
-- ----------------------------

SET NAMES utf8mb4;

-- ----------------------------
-- 1、学习时长按天汇总
-- ----------------------------
create table if not exists learning_study_record (
  record_id   bigint   not null auto_increment comment '主键',
  user_id     bigint   not null               comment '用户ID（逻辑关联 sys_user.user_id）',
  study_date  date     not null               comment '学习日期（Asia/Shanghai）',
  seconds     int      not null default 0     comment '当天累计学习秒数',
  create_time datetime default null           comment '首次学习时间',
  update_time datetime default null           comment '最近更新时间',
  primary key (record_id),
  unique key uk_study_user_date (user_id, study_date),
  key idx_study_date (study_date)
) engine = innodb auto_increment = 1 default charset = utf8mb4 comment = '学习时长按天汇总';

-- ----------------------------
-- 2、课程小节学习进度
-- ----------------------------
create table if not exists learning_course_progress (
  progress_id     bigint   not null auto_increment comment '主键',
  user_id         bigint   not null               comment '用户ID（逻辑关联 sys_user.user_id）',
  course_id       bigint   not null               comment '课程ID（逻辑关联 learning_course.course_id）',
  video_id        bigint   not null               comment '课程小节ID（逻辑关联 learning_course_video.video_id）',
  finished        tinyint  not null default 0     comment '是否学完（0否 1是，置 1 后不会回退）',
  watched_seconds int      not null default 0     comment '该小节累计学习秒数',
  create_time     datetime default null           comment '首次学习时间',
  update_time     datetime default null           comment '最近更新时间',
  primary key (progress_id),
  unique key uk_progress_user_video (user_id, video_id),
  key idx_progress_user_course (user_id, course_id)
) engine = innodb auto_increment = 1 default charset = utf8mb4 comment = '课程小节学习进度';

-- ----------------------------
-- 自检
-- ----------------------------
select table_name, table_comment from information_schema.tables
 where table_schema = database() and table_name in ('learning_study_record', 'learning_course_progress');

select table_name, index_name, non_unique from information_schema.statistics
 where table_schema = database()
   and table_name in ('learning_study_record', 'learning_course_progress')
   and non_unique = 0
 order by table_name, index_name;

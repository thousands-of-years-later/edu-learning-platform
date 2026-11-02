-- ----------------------------
-- 课程小节 增加「时长」列（learning_course_video.duration）
--
-- 背景：课程小节表原来没有时长，导致
--   ① 课程目录/详情无法显示"这节课多久"；
--   ② 学习进度只能显示"看了多少秒"，算不出百分比；
--   ③ 学习数据里的"学习时长"只能靠客户端上报，无法校验。
--
-- 口径：
--   · 单位【秒】；后台录课时填写，可留空（历史数据没有时长）；
--   · 客户端播放时可通过学习上报（/api/v1/learn/report 的 duration 参数）补填，
--     只在小节时长为空时写一次，不覆盖管理员填过的值；
--   · 不参与任何计数/排序，纯展示与进度计算用。
--
-- 前置条件：已执行 sql/learning_course.sql（本脚本只补列）。
-- 执行方式：
--   mysql --default-character-set=utf8mb4 -h43.142.9.214 -P3306 -uedu_dev_03 -p learning_platform < sql/learning_course_video_duration.sql
--
-- 可重复执行（幂等）。
--
-- ⚠️ 必须在【部署新版应用之前】执行：小节查询会 select duration，
--    先发应用会报 Unknown column 'v.duration'。
-- ----------------------------

SET NAMES utf8mb4;

set @ddl := (
  select if(count(*) = 0,
            'alter table learning_course_video add column duration int default null comment ''时长（秒，可空）''',
            'do 0')
    from information_schema.columns
   where table_schema = database()
     and table_name = 'learning_course_video'
     and column_name = 'duration'
);

prepare stmt from @ddl;
execute stmt;
deallocate prepare stmt;

-- ----------------------------
-- 自检
-- ----------------------------
select column_name, column_type, is_nullable, column_comment
  from information_schema.columns
 where table_schema = database()
   and table_name = 'learning_course_video'
 order by ordinal_position;

select count(*) as video_rows, sum(duration is null) as 未填时长的条数
  from learning_course_video where del_flag = '0';

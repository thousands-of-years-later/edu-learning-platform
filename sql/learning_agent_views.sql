-- ----------------------------
-- 智能体只读视图补充：
--   1) v_agent_course_video 增加 video_url（视频总结/ASR 用）
--   2) v_agent_course_chapter 暴露章节正文（RAG 用）
-- 可重复执行；生产由 DBA 执行并授权 edu_ro
-- ----------------------------
SET NAMES utf8mb4;

create or replace algorithm=undefined view v_agent_course_video as
select v.video_id as video_id,
       v.course_id as course_id,
       c.course_name as course_name,
       v.chapter_id as chapter_id,
       ch.chapter_title as chapter_title,
       v.video_title as video_title,
       v.video_url as video_url,
       v.duration as duration,
       round(v.duration / 60, 1) as duration_min,
       v.order_num as order_num,
       v.create_time as create_time
  from learning_course_video v
  join learning_course c on c.course_id = v.course_id and c.del_flag = '0'
  left join learning_course_chapter ch on ch.chapter_id = v.chapter_id and ch.del_flag = '0'
 where v.del_flag = '0';

create or replace algorithm=undefined view v_agent_course_chapter as
select ch.chapter_id as chapter_id,
       ch.course_id as course_id,
       c.course_name as course_name,
       ch.chapter_title as chapter_title,
       ch.chapter_content as chapter_content,
       ch.order_num as order_num,
       ch.create_time as create_time
  from learning_course_chapter ch
  join learning_course c on c.course_id = ch.course_id and c.del_flag = '0'
 where ch.del_flag = '0';

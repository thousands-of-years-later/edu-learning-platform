-- ----------------------------
-- 按关联表重算三个内容表的真实点赞/收藏数
--
-- 用途：
--   1. 首次上线后，把存量内容的假计数（管理员手工填的）校正为真实值
--   2. 以后任何怀疑计数漂移时，重跑一遍即可
--
-- 本脚本【只读关联表 + 覆盖计数列】，不碰其它字段，可重复执行（幂等）。
--
-- 执行方式：
--   mysql --default-character-set=utf8mb4 -h43.142.9.214 -P3306 -uedu_dev_03 -p learning_platform < sql/learning_interaction_fix_count.sql
--
-- ⚠️ 执行前请确认：这一步会把所有存量内容的计数改成真实值（没有互动的会变成 0）
-- ----------------------------

SET NAMES utf8mb4;

-- 帖子
update learning_post p set
  p.like_count = (select count(*) from learning_interaction i
                   where i.target_type = '1' and i.target_id = p.post_id and i.type = '1'),
  p.favorite_count = (select count(*) from learning_interaction i
                   where i.target_type = '1' and i.target_id = p.post_id and i.type = '2');

-- 课程
update learning_course c set
  c.like_count = (select count(*) from learning_interaction i
                   where i.target_type = '2' and i.target_id = c.course_id and i.type = '1'),
  c.favorite_count = (select count(*) from learning_interaction i
                   where i.target_type = '2' and i.target_id = c.course_id and i.type = '2');

-- 短视频
update learning_short_video v set
  v.like_count = (select count(*) from learning_interaction i
                   where i.target_type = '3' and i.target_id = v.video_id and i.type = '1'),
  v.favorite_count = (select count(*) from learning_interaction i
                   where i.target_type = '3' and i.target_id = v.video_id and i.type = '2');

-- 自检：三张表的计数与关联表是否一致（期望三行都是 0）
select 'learning_post' as tbl, count(*) as mismatch from learning_post p
 where p.like_count <> (select count(*) from learning_interaction i where i.target_type='1' and i.target_id=p.post_id and i.type='1')
    or p.favorite_count <> (select count(*) from learning_interaction i where i.target_type='1' and i.target_id=p.post_id and i.type='2')
union all
select 'learning_course', count(*) from learning_course c
 where c.like_count <> (select count(*) from learning_interaction i where i.target_type='2' and i.target_id=c.course_id and i.type='1')
    or c.favorite_count <> (select count(*) from learning_interaction i where i.target_type='2' and i.target_id=c.course_id and i.type='2')
union all
select 'learning_short_video', count(*) from learning_short_video v
 where v.like_count <> (select count(*) from learning_interaction i where i.target_type='3' and i.target_id=v.video_id and i.type='1')
    or v.favorite_count <> (select count(*) from learning_interaction i where i.target_type='3' and i.target_id=v.video_id and i.type='2');
-- 期望：三行都是 0
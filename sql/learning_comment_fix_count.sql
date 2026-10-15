-- ----------------------------
-- 按评论表重算三个内容表的真实评论数
--
-- 用途：
--   1. 首次上线后，把存量内容的假计数（管理员在后台编辑表单里手工填的）校正为真实值
--   2. 以后任何怀疑计数漂移时，重跑一遍即可
--
-- 本脚本【只读评论表 + 覆盖 comment_count 列】，不碰其它字段，可重复执行（幂等）。
--
-- 执行方式：
--   mysql --default-character-set=utf8mb4 -h43.142.9.214 -P3306 -uedu_dev_03 -p learning_platform < sql/learning_comment_fix_count.sql
--
-- ⚠️ 执行前请确认：这一步会把所有存量内容的评论数改成真实值（没有评论的会变成 0）
-- ----------------------------

SET NAMES utf8mb4;

-- 帖子
update learning_post p set
  p.comment_count = (select count(*) from learning_comment c
                      where c.target_type = '1' and c.target_id = p.post_id and c.del_flag = '0');

-- 课程
update learning_course co set
  co.comment_count = (select count(*) from learning_comment c
                       where c.target_type = '2' and c.target_id = co.course_id and c.del_flag = '0');

-- 短视频
update learning_short_video v set
  v.comment_count = (select count(*) from learning_comment c
                      where c.target_type = '3' and c.target_id = v.video_id and c.del_flag = '0');

-- 自检：三张表的计数与评论表是否一致（期望三行都是 0）
select 'learning_post' as tbl, count(*) as mismatch from learning_post p
 where p.comment_count <> (select count(*) from learning_comment c
                            where c.target_type = '1' and c.target_id = p.post_id and c.del_flag = '0')
union all
select 'learning_course', count(*) from learning_course co
 where co.comment_count <> (select count(*) from learning_comment c
                             where c.target_type = '2' and c.target_id = co.course_id and c.del_flag = '0')
union all
select 'learning_short_video', count(*) from learning_short_video v
 where v.comment_count <> (select count(*) from learning_comment c
                            where c.target_type = '3' and c.target_id = v.video_id and c.del_flag = '0');
-- 期望：三行都是 0

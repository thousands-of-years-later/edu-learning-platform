-- ----------------------------
-- 清理「已删除内容」遗留的互动/评论孤儿数据，并把已删除内容的计数列清零
--
-- 背景：
--   删除短视频 / 帖子 / 课程走的是逻辑删除（del_flag='2'）。在此之前的服务层没有级联清理，
--   挂在它们上面的点赞收藏（learning_interaction）与评论（learning_comment）留在了库里，
--   成为孤儿数据，会把 learning_interaction_fix_count.sql / learning_comment_fix_count.sql
--   的对账结果抬歪。
--   修复之后【新发生】的删除已由服务层在同一事务里级联清理，本脚本只清【存量】。
--
-- 本脚本做四件事：
--   1) 硬删除指向已删除内容的 learning_interaction 行（该表无 del_flag，取消点赞本就是硬删除）
--   2) 软删除指向已删除内容的 learning_comment 行（del_flag='2'，与评论模块同一语义）
--   3) 把三张内容表里 del_flag='2' 的行的 like_count / favorite_count / comment_count 清零
--   4) 自检：以上三类残留都应为 0
--
-- 「孤儿」在本脚本里的定义是【目标已被软删除】（del_flag='2'）。指向完全不存在 target_id 的行
-- 不处理：本系统不存在内容硬删除，那种行属于另一类数据异常，值得单独排查而不是被静默删掉。
--
-- 不碰的字段：内容表的 view_count、learning_course 的 rating_count / rating_score（评分不属本脚本
-- 范围，见 learning_course_rating.sql），也不碰任何 del_flag='0' 的内容行。
-- 可重复执行（幂等）：第二遍 1/2/3 均影响 0 行，自检仍为 0。
--
-- ⚠️ 第 1 步是【物理删除】learning_interaction 行，不可恢复。执行前先备份这两张子表：
--   mysqldump --default-character-set=utf8mb4 -h43.142.9.214 -P3306 -uedu_dev_03 -p learning_platform \
--     learning_interaction learning_comment > backup_before_orphan_cleanup.sql
--
-- 建议顺序：先跑本脚本，再跑 learning_interaction_fix_count.sql / learning_comment_fix_count.sql
-- 复核（两者的 UPDATE 应不再改动任何行，自检应输出 0）。
--
-- 执行方式：
--   mysql --default-character-set=utf8mb4 -h43.142.9.214 -P3306 -uedu_dev_03 -p learning_platform < sql/learning_orphan_cleanup.sql
-- ----------------------------

SET NAMES utf8mb4;

-- ----------------------------
-- 1、硬删除指向已删除内容的点赞收藏
--    MySQL 多表 delete 用 join 写法：能走 idx_target (target_type, target_id, type)，
--    也回避「子查询里引用被删表」的限制。
--    target_type 必须带上：三种目标的 ID 各自独立编号，只按 target_id 过滤会串到别的类型上。
-- ----------------------------
delete i from learning_interaction i
  join learning_post p on p.post_id = i.target_id
 where i.target_type = '1' and p.del_flag = '2';

delete i from learning_interaction i
  join learning_course c on c.course_id = i.target_id
 where i.target_type = '2' and c.del_flag = '2';

delete i from learning_interaction i
  join learning_short_video v on v.video_id = i.target_id
 where i.target_type = '3' and v.del_flag = '2';

-- ----------------------------
-- 2、软删除指向已删除内容的评论
--    c.del_flag = '0' 保证重复执行不会反复改写 update_time。
-- ----------------------------
update learning_comment c
  join learning_post p on p.post_id = c.target_id
   set c.del_flag = '2', c.update_time = sysdate()
 where c.target_type = '1' and c.del_flag = '0' and p.del_flag = '2';

update learning_comment c
  join learning_course co on co.course_id = c.target_id
   set c.del_flag = '2', c.update_time = sysdate()
 where c.target_type = '2' and c.del_flag = '0' and co.del_flag = '2';

update learning_comment c
  join learning_short_video v on v.video_id = c.target_id
   set c.del_flag = '2', c.update_time = sysdate()
 where c.target_type = '3' and c.del_flag = '0' and v.del_flag = '2';

-- ----------------------------
-- 3、已删除内容的计数列清零（与修复后删除接口写的是同一组列；view_count / 评分列不碰）
--    非零判断让第二遍执行影响 0 行，也避免无谓写入。
-- ----------------------------
update learning_post
   set like_count = 0, favorite_count = 0, comment_count = 0
 where del_flag = '2'
   and (like_count <> 0 or favorite_count <> 0 or comment_count <> 0);

update learning_course
   set like_count = 0, favorite_count = 0, comment_count = 0
 where del_flag = '2'
   and (like_count <> 0 or favorite_count <> 0 or comment_count <> 0);

update learning_short_video
   set like_count = 0, favorite_count = 0, comment_count = 0
 where del_flag = '2'
   and (like_count <> 0 or favorite_count <> 0 or comment_count <> 0);

-- ----------------------------
-- 4、自检
-- ----------------------------

-- 4.1 指向已删除内容的点赞收藏（期望三行都是 0）
select 'learning_interaction' as tbl, '1' as target_type, count(*) as orphan_rows
  from learning_interaction i join learning_post p on p.post_id = i.target_id
 where i.target_type = '1' and p.del_flag = '2'
union all
select 'learning_interaction', '2', count(*)
  from learning_interaction i join learning_course c on c.course_id = i.target_id
 where i.target_type = '2' and c.del_flag = '2'
union all
select 'learning_interaction', '3', count(*)
  from learning_interaction i join learning_short_video v on v.video_id = i.target_id
 where i.target_type = '3' and v.del_flag = '2';

-- 4.2 指向已删除内容且仍未删除的评论（期望三行都是 0）
select 'learning_comment' as tbl, '1' as target_type, count(*) as orphan_rows
  from learning_comment c join learning_post p on p.post_id = c.target_id
 where c.target_type = '1' and c.del_flag = '0' and p.del_flag = '2'
union all
select 'learning_comment', '2', count(*)
  from learning_comment c join learning_course co on co.course_id = c.target_id
 where c.target_type = '2' and c.del_flag = '0' and co.del_flag = '2'
union all
select 'learning_comment', '3', count(*)
  from learning_comment c join learning_short_video v on v.video_id = c.target_id
 where c.target_type = '3' and c.del_flag = '0' and v.del_flag = '2';

-- 4.3 已删除内容仍有非零计数（期望三行都是 0）
select 'learning_post' as tbl, count(*) as not_zeroed
  from learning_post
 where del_flag = '2' and (like_count <> 0 or favorite_count <> 0 or comment_count <> 0)
union all
select 'learning_course', count(*)
  from learning_course
 where del_flag = '2' and (like_count <> 0 or favorite_count <> 0 or comment_count <> 0)
union all
select 'learning_short_video', count(*)
  from learning_short_video
 where del_flag = '2' and (like_count <> 0 or favorite_count <> 0 or comment_count <> 0);
-- 期望：以上三段查询输出的每一行都是 0

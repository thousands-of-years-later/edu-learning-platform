-- ----------------------------
-- 按评分表重算 learning_course 的评分冗余列（rating_count / rating_score）
--
-- 用途：冗余统计怀疑漂移时重跑一遍即可。
--       接口返回的平均分/评分人数【不读】这两列（以评分表 count(*)/sum(score) 为准），
--       所以即使漂移也只影响后台课程列表的展示。
--
-- 本脚本【只读评分表、只覆盖这两列】，可重复执行（幂等）。
--
-- 执行方式：
--   mysql --default-character-set=utf8mb4 -h43.142.9.214 -P3306 -uedu_dev_03 -p learning_platform < sql/learning_course_rating_fix_count.sql
-- ----------------------------

SET NAMES utf8mb4;

update learning_course c set
  c.rating_count = (select count(*) from learning_course_rating r where r.course_id = c.course_id),
  c.rating_score = (select ifnull(sum(r.score), 0) from learning_course_rating r where r.course_id = c.course_id);

-- 自检：不一致的课程数（期望 0）
select count(*) as mismatch from learning_course c
 where c.rating_count <> (select count(*) from learning_course_rating r where r.course_id = c.course_id)
    or c.rating_score <> (select ifnull(sum(r.score), 0) from learning_course_rating r where r.course_id = c.course_id);
-- 期望：0

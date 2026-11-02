-- ----------------------------
-- 课程评分 增加「文字评价」列（learning_course_rating.content）
--
-- 背景：设计稿 14_课程评价 的评价卡片除了星级，还有一段文字（"说说这门课的收获"）。
--       原表只有 score（1~5 分），没有文字列，只能显示分数，做不出评价列表。
--
-- 口径：
--   · 文字可空：只打星级不写文字是允许的，列表里那条评价就只显示分数；
--   · 长度上限 500 字（服务层校验，列宽 500 与校验保持一致）；
--   · 改分/改评价时文字整体覆盖（传空 = 清空文字）；
--   · 只加列，不动 score、不动唯一索引 uk_course_user、不动计数逻辑。
--
-- 前置条件：已执行 sql/learning_course_rating.sql（本脚本只补列）。
-- 执行方式：
--   mysql --default-character-set=utf8mb4 -h43.142.9.214 -P3306 -uedu_dev_03 -p learning_platform < sql/learning_course_rating_content.sql
--
-- 可重复执行（幂等）：加列前查 information_schema，已存在则 do 0。
--
-- ⚠️ 必须在【部署新版应用之前】执行：评价列表会 select content，
--    先发应用会报 Unknown column 'content'。
-- ----------------------------

SET NAMES utf8mb4;

set @ddl := (
  select if(count(*) = 0,
            'alter table learning_course_rating add column content varchar(500) default null comment ''文字评价（可空）''',
            'do 0')
    from information_schema.columns
   where table_schema = database()
     and table_name = 'learning_course_rating'
     and column_name = 'content'
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
   and table_name = 'learning_course_rating'
 order by ordinal_position;

select count(*) as rating_rows from learning_course_rating;

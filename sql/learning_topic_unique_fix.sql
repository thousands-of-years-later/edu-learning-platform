-- ----------------------------
-- 话题名称唯一索引修正（learning_topic）
--
-- 问题：learning_topic 原本建的是 unique key uk_topic_name (topic_name)，
--       但话题是【逻辑删除】（del_flag='2'）。一条话题被软删后，库里那行还在，
--       再新建同名话题就会撞唯一键，管理员会看到"话题名称已存在"却找不到任何同名话题。
--
-- 改法：去掉数据库唯一索引，换成普通索引；唯一性改由【应用层】校验
--       （TopicServiceImpl.checkTopicNameUnique 只统计 del_flag='0' 的行）。
--       与若依里课程名称、分类名称的处理方式一致：软删表不用唯一索引做业务约束。
--
-- 前提：已执行 sql/learning_topic.sql。
-- 执行方式：
--   mysql --default-character-set=utf8mb4 -h43.142.9.214 -P3306 -uedu_dev_03 -p learning_platform < sql/learning_topic_unique_fix.sql
--
-- 可重复执行（幂等）。
-- ----------------------------

SET NAMES utf8mb4;

-- 1、删除唯一索引（存在才删）
set @dropDdl := (
  select if(count(*) > 0, 'alter table learning_topic drop index uk_topic_name', 'do 0')
    from information_schema.statistics
   where table_schema = database()
     and table_name = 'learning_topic'
     and index_name = 'uk_topic_name'
);
prepare stmt from @dropDdl;
execute stmt;
deallocate prepare stmt;

-- 2、加普通索引（不存在才加），保证按名称查询仍然走索引
set @addDdl := (
  select if(count(*) = 0, 'alter table learning_topic add index idx_topic_name (topic_name)', 'do 0')
    from information_schema.statistics
   where table_schema = database()
     and table_name = 'learning_topic'
     and index_name = 'idx_topic_name'
);
prepare stmt2 from @addDdl;
execute stmt2;
deallocate prepare stmt2;

-- ----------------------------
-- 自检：期望看到 idx_topic_name 且 non_unique=1，没有 uk_topic_name
-- ----------------------------
select index_name, non_unique, group_concat(column_name order by seq_in_index) as columns
  from information_schema.statistics
 where table_schema = database() and table_name = 'learning_topic'
 group by index_name, non_unique;

select topic_name, count(*) as cnt
  from learning_topic
 where del_flag = '0'
 group by topic_name
having count(*) > 1;
-- 期望：空（应用层已防重，这里只是核对存量数据没有重名）

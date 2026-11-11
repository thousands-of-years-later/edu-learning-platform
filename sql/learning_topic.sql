-- ----------------------------
-- 话题 learning_topic / learning_post_topic
--
-- 对应设计稿 17_话题页：点开一个话题（如 #Android），看这个话题下的文章列表。
-- 文章详情（16 页）标题下面的 #Android #Kotlin #Compose 也来自这里。
--
-- 两张表：
--   learning_topic       话题本身（名称唯一、可停用）
--   learning_post_topic  文章与话题的多对多关联（一篇文章可选多个话题）
--
-- 口径（产品确认）：
--   · 话题由【管理员】创建（后台维护），用户在发帖时从已有话题里选，不允许自由输入，
--     避免出现 "安卓 / Android / android" 这种同义话题把内容拆散；
--   · 一篇文章最多选 5 个话题；同一文章重复选同一话题由唯一索引挡住（insert ignore）；
--   · 话题的"文章数"实时用关联表 count 算，不加冗余计数字段（量小，避免一致性维护）；
--   · 只有【已发布】文章计入话题文章数，草稿/待审核不计入。
--
-- 执行方式：
--   mysql --default-character-set=utf8mb4 -h43.142.9.214 -P3306 -uedu_dev_03 -p learning_platform < sql/learning_topic.sql
--
-- 可重复执行（幂等）：建表用 if not exists；示例话题用 where not exists 判重。
-- ----------------------------

SET NAMES utf8mb4;

-- ----------------------------
-- 1、话题表
-- ----------------------------
create table if not exists learning_topic (
  topic_id     bigint      not null auto_increment comment '话题ID',
  topic_name   varchar(50) not null                comment '话题名（不含 # 号，如 Android）',
  topic_desc   varchar(200) default null           comment '话题简介（话题页副标题）',
  status       char(1)     default '0'             comment '状态（0正常 1停用）',
  del_flag     char(1)     default '0'             comment '删除标志（0代表存在 2代表删除）',
  create_by    varchar(64) default ''              comment '创建者',
  create_time  datetime    default null            comment '创建时间',
  update_by    varchar(64) default ''              comment '更新者',
  update_time  datetime    default null            comment '更新时间',
  primary key (topic_id),
  -- 名称【不做数据库唯一索引】：话题是逻辑删除，软删后同名重建会撞唯一键；
  -- 唯一性由应用层校验（只统计 del_flag='0' 的行），见 TopicServiceImpl。
  key idx_topic_name (topic_name)
) engine = innodb auto_increment = 1 default charset = utf8mb4 comment = '话题表';

-- ----------------------------
-- 2、文章-话题关联表
-- ----------------------------
create table if not exists learning_post_topic (
  id          bigint   not null auto_increment comment '主键',
  post_id     bigint   not null               comment '文章ID（逻辑关联 learning_post.post_id）',
  topic_id    bigint   not null               comment '话题ID（逻辑关联 learning_topic.topic_id）',
  create_time datetime default null           comment '关联时间',
  primary key (id),
  unique key uk_post_topic (post_id, topic_id),
  key idx_topic (topic_id)
) engine = innodb auto_increment = 1 default charset = utf8mb4 comment = '文章-话题关联表';

-- ----------------------------
-- 3、示例话题（管理后台还没做页面，先给几个初始话题，方便前端联调）
-- ----------------------------
insert into learning_topic (topic_name, topic_desc, status, del_flag, create_by, create_time)
select 'Android', 'Android 开发相关', '0', '0', 'admin', sysdate() from dual
 where not exists (select 1 from learning_topic where topic_name = 'Android');
insert into learning_topic (topic_name, topic_desc, status, del_flag, create_by, create_time)
select 'Kotlin', 'Kotlin 语言与协程', '0', '0', 'admin', sysdate() from dual
 where not exists (select 1 from learning_topic where topic_name = 'Kotlin');
insert into learning_topic (topic_name, topic_desc, status, del_flag, create_by, create_time)
select 'Compose', 'Jetpack Compose', '0', '0', 'admin', sysdate() from dual
 where not exists (select 1 from learning_topic where topic_name = 'Compose');
insert into learning_topic (topic_name, topic_desc, status, del_flag, create_by, create_time)
select 'Java', 'Java 基础与进阶', '0', '0', 'admin', sysdate() from dual
 where not exists (select 1 from learning_topic where topic_name = 'Java');
insert into learning_topic (topic_name, topic_desc, status, del_flag, create_by, create_time)
select 'MySQL', '数据库与 SQL 优化', '0', '0', 'admin', sysdate() from dual
 where not exists (select 1 from learning_topic where topic_name = 'MySQL');
insert into learning_topic (topic_name, topic_desc, status, del_flag, create_by, create_time)
select 'Redis', 'Redis 原理与实战', '0', '0', 'admin', sysdate() from dual
 where not exists (select 1 from learning_topic where topic_name = 'Redis');
insert into learning_topic (topic_name, topic_desc, status, del_flag, create_by, create_time)
select '并发编程', '多线程与并发', '0', '0', 'admin', sysdate() from dual
 where not exists (select 1 from learning_topic where topic_name = '并发编程');

-- ----------------------------
-- 自检
-- ----------------------------
select table_name, table_comment from information_schema.tables
 where table_schema = database() and table_name in ('learning_topic', 'learning_post_topic');

select count(*) as topic_rows from learning_topic;
select count(*) as post_topic_rows from learning_post_topic;

-- ----------------------------
-- 社区帖子（Post）表结构 + 帖子状态字典
--
-- 说明：
-- 1. 帖子是社区模块的内容主体，挂在社区类别（learning_category.category_type = 'article'）下。
--    正文是 Markdown 原文（文字 + 图片，无视频），图片以 ![](/profile/...) 相对路径内联，
--    由 EasyMDE 的图片上传钩子调用 /common/upload 得到。
-- 2. 点赞、收藏、评论与 course 保持一致：只做三个计数列，不建「每个用户一条记录」的关联表。
-- 3. 软删除（del_flag：0代表存在 2代表删除），与 learning_category / learning_course 一致。
-- 4. 外键一律为逻辑外键，不加物理 FK：
--    learning_post.category_id -> learning_category.category_id
--    learning_post.user_id     -> sys_user.user_id
-- 5. 本脚本可重复执行（幂等），对已存在的库不会破坏数据。
--
-- 执行方式（必须带字符集参数，否则中文注释会乱码）：
--   mysql --default-character-set=utf8mb4 -h43.142.9.214 -P3306 -uedu_dev_03 -p learning_platform < sql/learning_post.sql
--
-- 注意：本表建好前，社区类别（article 类型）的删除保护统计不到帖子
--       （CategoryMapper.countPostByCategoryId 会报表不存在），请先执行本脚本。
-- 菜单与按钮权限在 sql/learning_post_menu.sql。
-- ----------------------------

SET NAMES utf8mb4;

-- ----------------------------
-- 1、帖子表
-- ----------------------------

create table if not exists learning_post (
  post_id        bigint(20)   not null auto_increment comment '帖子ID',
  post_title     varchar(200) not null                comment '帖子标题',
  category_id    bigint(20)   default null            comment '所属类别ID（逻辑关联 learning_category.category_id，category_type=article）',
  user_id        bigint(20)   not null                comment '作者用户ID（逻辑关联 sys_user.user_id）',
  content        longtext                             comment '正文（Markdown 原文，图片以 ![](/profile/...) 内联）',
  like_count     int(11)      default 0               comment '点赞数',
  favorite_count int(11)      default 0               comment '收藏数',
  comment_count  int(11)      default 0               comment '评论数',
  status         char(1)      default '1'             comment '状态（0已发布 1草稿 2已下架）',
  del_flag       char(1)      default '0'             comment '删除标志（0代表存在 2代表删除）',
  create_by      varchar(64)  default ''              comment '创建者',
  create_time    datetime                             comment '创建时间',
  update_by      varchar(64)  default ''              comment '更新者',
  update_time    datetime                             comment '更新时间',
  remark         varchar(500) default null            comment '备注',
  primary key (post_id),
  key idx_post_category (category_id, del_flag),
  key idx_post_user (user_id, del_flag),
  key idx_post_status (status, del_flag)
) engine=innodb auto_increment=100 comment = '学习平台社区帖子表';

-- 关于唯一索引：帖子标题是用户生成内容，不做唯一约束（两个用户可以取同名标题），
-- 因此这里既不建唯一索引，应用层也不做 checkPostTitleUnique。

-- status 默认取 '1'（草稿）：应用层每次写入都会显式带上状态，这个默认值只对裸 SQL 插入生效；
-- 草稿是可以恢复的，误发布不可恢复，所以默认取草稿。
-- 注意本表没有独立的封面/摘要字段 —— 图片直接写在 Markdown 正文里，符合「图片和文字、md 格式」的要求。

-- ----------------------------
-- 2、新增字典：帖子状态（列表页状态列与表单状态单选都用它渲染）
-- ----------------------------

insert into sys_dict_type (dict_name, dict_type, status, create_by, create_time, remark)
select '帖子状态', 'sys_post_status', '0', 'admin', sysdate(), '社区帖子状态列表'
  from dual
 where not exists (select 1 from sys_dict_type where dict_type = 'sys_post_status');

-- is_default 只影响表单里的单选默认选中项：默认选中「草稿」，避免误发布。
insert into sys_dict_data (dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status, create_by, create_time, remark)
select 1, '已发布', '0', 'sys_post_status', '', 'success', 'N', '0', 'admin', sysdate(), '帖子已发布，所有人可见'
  from dual
 where not exists (select 1 from sys_dict_data where dict_type = 'sys_post_status' and dict_value = '0');

insert into sys_dict_data (dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status, create_by, create_time, remark)
select 2, '草稿', '1', 'sys_post_status', '', 'info', 'Y', '0', 'admin', sysdate(), '帖子草稿，仅作者与管理员可见'
  from dual
 where not exists (select 1 from sys_dict_data where dict_type = 'sys_post_status' and dict_value = '1');

insert into sys_dict_data (dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status, create_by, create_time, remark)
select 3, '已下架', '2', 'sys_post_status', '', 'danger', 'N', '0', 'admin', sysdate(), '帖子已下架，仅管理员可恢复'
  from dual
 where not exists (select 1 from sys_dict_data where dict_type = 'sys_post_status' and dict_value = '2');

-- ----------------------------
-- 3、自检
-- ----------------------------

select table_name, table_comment
  from information_schema.tables
 where table_schema = database()
   and table_name = 'learning_post';
-- 期望：1 张表

select dict_type, dict_label, dict_value, list_class, is_default, status
  from sys_dict_data
 where dict_type = 'sys_post_status'
 order by dict_sort;
-- 期望：3 行（0已发布 / 1草稿(默认) / 2已下架）

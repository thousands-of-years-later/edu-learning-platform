-- ----------------------------
-- 点赞收藏表 learning_interaction
--
-- 背景（重要）：原约定是「只做三个计数列，不建每个用户一条记录的关联表」，
-- 但那种模型做不到「判断我赞过没 / 取消点赞 / 防重复点赞」，
-- 所以这里改为建关联表，计数字段降级为【由本表维护的冗余统计】。
--
-- 设计：
--   一张表管三种内容（1帖子 2课程 3短视频）× 两种行为（1点赞 2收藏）
--   取消点赞/收藏是【硬删除】（直接删行），因此唯一索引是安全的 ——
--   不存在 learning_category 那种「软删除占位导致名字永久不可用」的问题。
--   唯一索引同时负责防重复与并发安全。
--
-- 计数维护约定：
--   learning_post / learning_course / learning_short_video 的
--   like_count / favorite_count 一律【由后端本模块维护】，
--   后台新增/编辑内容时不再写入这两个字段（对应 Mapper 已去掉这两列）。
--
-- 本脚本可重复执行（幂等）。
--
-- 执行方式：
--   mysql --default-character-set=utf8mb4 -h43.142.9.214 -P3306 -uedu_dev_03 -p learning_platform < sql/learning_interaction.sql
-- ----------------------------

SET NAMES utf8mb4;

create table if not exists learning_interaction (
  id          bigint(20)  not null auto_increment comment '主键',
  user_id     bigint(20)  not null                comment '用户ID（逻辑关联 sys_user.user_id）',
  target_type char(1)     not null                comment '目标类型（1帖子 2课程 3短视频）',
  target_id   bigint(20)  not null                comment '目标ID',
  type        char(1)     not null                comment '行为类型（1点赞 2收藏）',
  create_time datetime    default null            comment '发生时间',
  primary key (id),
  unique key uk_user_target (user_id, target_type, target_id, type),
  key idx_target (target_type, target_id, type)
) engine=innodb auto_increment=100 comment = '学习平台点赞收藏表';

-- ----------------------------
-- 字典：目标类型 / 行为类型（小程序与后台展示都用它）
-- ----------------------------

insert into sys_dict_type (dict_name, dict_type, status, create_by, create_time, remark)
select '互动目标类型', 'sys_interaction_target', '0', 'admin', sysdate(), '点赞收藏的目标类型'
  from dual where not exists (select 1 from sys_dict_type where dict_type = 'sys_interaction_target');

insert into sys_dict_data (dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status, create_by, create_time, remark)
select 1, '社区帖子', '1', 'sys_interaction_target', '', 'primary', 'Y', '0', 'admin', sysdate(), ''
  from dual where not exists (select 1 from sys_dict_data where dict_type = 'sys_interaction_target' and dict_value = '1');
insert into sys_dict_data (dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status, create_by, create_time, remark)
select 2, '课程', '2', 'sys_interaction_target', '', 'success', 'N', '0', 'admin', sysdate(), ''
  from dual where not exists (select 1 from sys_dict_data where dict_type = 'sys_interaction_target' and dict_value = '2');
insert into sys_dict_data (dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status, create_by, create_time, remark)
select 3, '短视频', '3', 'sys_interaction_target', '', 'warning', 'N', '0', 'admin', sysdate(), ''
  from dual where not exists (select 1 from sys_dict_data where dict_type = 'sys_interaction_target' and dict_value = '3');

insert into sys_dict_type (dict_name, dict_type, status, create_by, create_time, remark)
select '互动类型', 'sys_interaction_type', '0', 'admin', sysdate(), '点赞 / 收藏'
  from dual where not exists (select 1 from sys_dict_type where dict_type = 'sys_interaction_type');

insert into sys_dict_data (dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status, create_by, create_time, remark)
select 1, '点赞', '1', 'sys_interaction_type', '', 'danger', 'Y', '0', 'admin', sysdate(), ''
  from dual where not exists (select 1 from sys_dict_data where dict_type = 'sys_interaction_type' and dict_value = '1');
insert into sys_dict_data (dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status, create_by, create_time, remark)
select 2, '收藏', '2', 'sys_interaction_type', '', 'info', 'N', '0', 'admin', sysdate(), ''
  from dual where not exists (select 1 from sys_dict_data where dict_type = 'sys_interaction_type' and dict_value = '2');

-- ----------------------------
-- 自检
-- ----------------------------
select table_name, table_comment from information_schema.tables
 where table_schema = database() and table_name = 'learning_interaction';
-- 期望：1 张表

select dict_type, dict_label, dict_value from sys_dict_data
 where dict_type in ('sys_interaction_target','sys_interaction_type') order by dict_type, dict_sort;
-- 期望：5 行（3 + 2）

select count(*) as current_rows from learning_interaction;
-- 期望：0
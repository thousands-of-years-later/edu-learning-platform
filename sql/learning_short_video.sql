-- ----------------------------
-- 短视频（ShortVideo）表结构 + 视频状态字典
--
-- 说明：
-- 1. 短视频是「视频」Tab 的内容主体，由管理员在后台录入（用户不投稿），
--    因此没有「可见性范围」「归属校验」那一套，写操作全部由菜单权限把关。
-- 2. 【没有分类】：短视频不挂 learning_category，全站一条信息流。
-- 3. 点赞、收藏、评论与 course / post 保持一致：只做三个计数列，
--    不建「每个用户一条记录」的关联表。
-- 4. 软删除（del_flag：0代表存在 2代表删除），与 learning_category / learning_course / learning_post 一致。
-- 5. 外键一律为逻辑外键，不加物理 FK：learning_short_video.user_id -> sys_user.user_id
-- 6. 本脚本可重复执行（幂等），对已存在的库不会破坏数据。
--
-- 执行方式（必须带字符集参数，否则中文注释会乱码）：
--   mysql --default-character-set=utf8mb4 -h43.142.9.214 -P3306 -uedu_dev_03 -p learning_platform < sql/learning_short_video.sql
--
-- 菜单与按钮权限在 sql/learning_short_video_menu.sql。
-- ----------------------------

SET NAMES utf8mb4;

-- ----------------------------
-- 1、短视频表
-- ----------------------------

create table if not exists learning_short_video (
  video_id       bigint(20)    not null auto_increment comment '短视频ID',
  video_title    varchar(200)  not null                comment '视频标题',
  user_id        bigint(20)    not null                comment '上传者用户ID（逻辑关联 sys_user.user_id）',
  video_url      varchar(500)  default ''              comment '视频地址（上传返回的相对路径 /profile/...）',
  cover_url      varchar(500)  default ''              comment '封面图片地址（上传返回的相对路径 /profile/...）',
  intro          varchar(1000) default ''              comment '视频简介',
  duration       int(11)       default 0               comment '时长（秒）',
  view_count     int(11)       default 0               comment '播放数',
  like_count     int(11)       default 0               comment '点赞数',
  favorite_count int(11)       default 0               comment '收藏数',
  comment_count  int(11)       default 0               comment '评论数',
  status         char(1)       default '0'             comment '状态（0正常 1停用）',
  del_flag       char(1)       default '0'             comment '删除标志（0代表存在 2代表删除）',
  create_by      varchar(64)   default ''              comment '创建者',
  create_time    datetime                              comment '创建时间',
  update_by      varchar(64)   default ''              comment '更新者',
  update_time    datetime                              comment '更新时间',
  remark         varchar(500)  default null            comment '备注',
  primary key (video_id),
  key idx_video_user (user_id, del_flag),
  key idx_video_status (status, del_flag)
) engine=innodb auto_increment=100 comment = '学习平台短视频表';

-- 关于唯一索引：与 learning_course / learning_post 同理，这里【不建】video_title 唯一索引 ——
-- 软删除下唯一索引会导致「删除过的标题永久无法再建」，唯一性由应用层
-- checkVideoTitleUnique 保证（只校验 del_flag='0'）。

-- duration 单位是秒（小程序上的「时长 00:45」就是这一列）。
-- view_count 是「1.2万 次播放」那一列，本阶段只做计数展示，不做去重。

-- ----------------------------
-- 2、新增字典：视频状态（列表页状态列与表单状态单选都用它渲染）
-- ----------------------------

insert into sys_dict_type (dict_name, dict_type, status, create_by, create_time, remark)
select '视频状态', 'sys_video_status', '0', 'admin', sysdate(), '短视频状态列表'
  from dual
 where not exists (select 1 from sys_dict_type where dict_type = 'sys_video_status');

insert into sys_dict_data (dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status, create_by, create_time, remark)
select 1, '正常', '0', 'sys_video_status', '', 'primary', 'Y', '0', 'admin', sysdate(), '正常，小程序可见'
  from dual
 where not exists (select 1 from sys_dict_data where dict_type = 'sys_video_status' and dict_value = '0');

insert into sys_dict_data (dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status, create_by, create_time, remark)
select 2, '停用', '1', 'sys_video_status', '', 'danger', 'N', '0', 'admin', sysdate(), '停用，小程序不可见'
  from dual
 where not exists (select 1 from sys_dict_data where dict_type = 'sys_video_status' and dict_value = '1');

-- ----------------------------
-- 3、自检
-- ----------------------------

select table_name, table_comment
  from information_schema.tables
 where table_schema = database()
   and table_name = 'learning_short_video';
-- 期望：1 张表

select dict_type, dict_label, dict_value, list_class, is_default, status
  from sys_dict_data
 where dict_type = 'sys_video_status'
 order by dict_sort;
-- 期望：2 行（0正常(默认) / 1停用）
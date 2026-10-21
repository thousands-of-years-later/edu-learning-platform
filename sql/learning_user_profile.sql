-- ----------------------------
-- 用户扩展资料 learning_user_profile（目前只有个人简介）
--
-- 背景（设计稿 27_编辑主页）：
--   编辑主页可改：头像、昵称、个人简介、性别（不公开/男/女）。
--   其中昵称 sys_user.user_name、头像 sys_user.avatar、性别 sys_user.sex 已有列，
--   只有「个人简介」没有地方存 —— 若依的 sys_user.remark 是后台备注，不能挪用，
--   所以单独建一张「用户扩展资料」表，一用户一行。
--   以后要加学校/专业/背景图之类，往这张表加列即可，不用动若依的用户表。
--
-- 另外把 sys_user.avatar 加宽到 varchar(500)：
--   原 varchar(100) 装不下 OSS 完整地址
--   （https://zhinengguanggaopingtai.oss-cn-shanghai.aliyuncs.com/upload/2026/10/21/xxx_20261021093000A001.png
--    大约 100~110 个字符），会报 1406 Data too long 或者把地址截断成打不开的图。
--   这是必要的列加宽，不改任何数据、不改任何逻辑。
--
-- 还有一件数据对齐：
--   性别字典 sys_user_sex 里 2 原来叫「未知」，而小程序端这个值表示「不公开」，
--   后台用户管理却显示「未知」，管理员会困惑。这里把文案统一成「不公开」——
--   纯文案改动，代码只按值 0/1/2 判断，没有任何逻辑依赖这个字。
--
-- 执行方式：
--   mysql --default-character-set=utf8mb4 -h43.142.9.214 -P3306 -uedu_dev_03 -p learning_platform < sql/learning_user_profile.sql
--
-- 可重复执行（幂等）：create table if not exists + information_schema 判断后再改列；
-- 字典文案那条用 where dict_label <> '不公开' 兜底，重复执行影响 0 行。
--
-- 执行完记得【重启应用】：若依字典走 Ehcache，不重启后台页面还会显示旧文案。
-- ----------------------------

SET NAMES utf8mb4;

-- ----------------------------
-- 1、用户扩展资料
-- ----------------------------
create table if not exists learning_user_profile (
  user_id     bigint       not null          comment '用户ID（sys_user.user_id，一用户一行）',
  bio         varchar(200) default null      comment '个人简介（纯文本，≤200字；null 表示没填）',
  create_time datetime     default null      comment '创建时间',
  update_time datetime     default null      comment '更新时间',
  primary key (user_id)
) engine = innodb default charset = utf8mb4 comment = '用户扩展资料（个人简介）';

-- ----------------------------
-- 2、头像列加宽（幂等）：varchar(100) 装不下 OSS 地址
-- ----------------------------
set @ddl = if((select character_maximum_length from information_schema.columns
                where table_schema = database() and table_name = 'sys_user'
                  and column_name = 'avatar') < 500,
              'alter table sys_user modify column avatar varchar(500) default null comment ''头像地址''',
              'do 0');
prepare s from @ddl; execute s; deallocate prepare s;

-- ----------------------------
-- 3、性别字典文案统一：2 未知 → 不公开（与小程序口径一致）
--    只改标签、不改值；where 带上 dict_label 判断，重复执行影响 0 行。
-- ----------------------------
update sys_dict_data
   set dict_label = '不公开', update_time = now()
 where dict_type = 'sys_user_sex'
   and dict_value = '2'
   and dict_label <> '不公开';

-- ----------------------------
-- 自检
-- ----------------------------
select table_name, table_comment from information_schema.tables
 where table_schema = database() and table_name = 'learning_user_profile';

select column_name, column_type, column_comment from information_schema.columns
 where table_schema = database() and table_name = 'learning_user_profile'
 order by ordinal_position;

-- 头像列必须是 500（否则 OSS 长地址会被截断）
select column_name, column_type, character_maximum_length
  from information_schema.columns
 where table_schema = database() and table_name = 'sys_user' and column_name = 'avatar';

select count(*) as profile_rows from learning_user_profile;

-- 性别字典（期望：0 男 / 1 女 / 2 不公开）
select dict_value, dict_label from sys_dict_data
 where dict_type = 'sys_user_sex' order by dict_sort;

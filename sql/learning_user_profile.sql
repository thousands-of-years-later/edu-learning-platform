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
-- 执行方式：
--   mysql --default-character-set=utf8mb4 -h43.142.9.214 -P3306 -uedu_dev_03 -p learning_platform < sql/learning_user_profile.sql
--
-- 可重复执行（幂等）：create table if not exists + information_schema 判断后再改列。
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

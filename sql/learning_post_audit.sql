-- ----------------------------
-- 社区帖子 审核链路 + 精华属性
--
-- 背景：
--   帖子原来只有 0已发布 / 1草稿 / 2已下架 三个状态，普通用户发帖即公开。
--   本脚本补「先审后发」链路，并新增「精华」属性（管理员在审核时指定）：
--     · status 复用增加两个字典值：3=待审核、4=已驳回（不加独立 audit_status 列）
--     · is_featured char(1) default '0'：精华标志（0否 1是），只有管理员能改
--     · audit_remark varchar(500)：最近一次审核备注（驳回原因/通过说明），作者在编辑页与详情页可见
--   状态流转：
--     非管理员 新增/编辑 选「发布」→ 3 待审核 → 管理员审核 → 0 已发布 / 4 已驳回
--     4 已驳回 → 作者改完再选「发布」→ 回到 3 待审核（已发布帖子被作者改动重提同理）
--     非管理员 选「草稿」→ 1 草稿；已下架(2) 只能由管理员解除
--
-- 前置条件：已执行 sql/learning_post.sql（建表与 sys_post_status 字典）
--           与 sql/learning_category_menu.sql（建「学习管理」目录）。
-- 本脚本可重复执行（幂等）：加列用 information_schema 判断后再动态执行 DDL
-- （MySQL 8 不支持 add column if not exists，沿用 sql/learning_course_rating.sql 的写法）。
--
-- ⚠️ 必须在【部署新版应用之前】执行本脚本：PostMapper.xml 会 select p.is_featured / p.audit_remark，
--    先发应用会报 Unknown column 'p.is_featured'。
--
-- 执行方式：
--   mysql --default-character-set=utf8mb4 -h43.142.9.214 -P3306 -uedu_dev_03 -p learning_platform < sql/learning_post_audit.sql
--
-- 注意：菜单、权限在登录时进 Shiro 缓存，字典在启动时进缓存（SysDictTypeServiceImpl.init），
--       执行完需要【重启应用并重新登录】。
-- ----------------------------

SET NAMES utf8mb4;

-- ----------------------------
-- 1、learning_post 幂等加两列 + 补 status 列注释
-- ----------------------------

set @ddl := (select if(count(*) = 0,
    'alter table learning_post add column is_featured char(1) default ''0'' comment ''精华标志（0否 1是，管理员在审核时或编辑时指定）'' after status',
    'do 0')
  from information_schema.columns
 where table_schema = database() and table_name = 'learning_post' and column_name = 'is_featured');
prepare stmt from @ddl;
execute stmt;
deallocate prepare stmt;

set @ddl := (select if(count(*) = 0,
    'alter table learning_post add column audit_remark varchar(500) default null comment ''最近一次审核备注（驳回原因；通过时可写说明；留空即清空）'' after remark',
    'do 0')
  from information_schema.columns
 where table_schema = database() and table_name = 'learning_post' and column_name = 'audit_remark');
prepare stmt from @ddl;
execute stmt;
deallocate prepare stmt;

-- status 列注释补上 3/4（信息性修改，不动数据；comment 里已含「待审核」时跳过）
-- 注：判断条件必须写在 where 里而不是 select 的 if() 里——select 里同时出现 count(*) 与
-- column_comment 会被 MySQL 8 默认的 only_full_group_by 拒绝（ERROR 1140）
set @ddl := (select if(count(*) = 0, 'do 0',
    'alter table learning_post modify column status char(1) default ''1'' comment ''状态（0已发布 1草稿 2已下架 3待审核 4已驳回）''')
  from information_schema.columns
 where table_schema = database() and table_name = 'learning_post' and column_name = 'status'
   and ifnull(column_comment, '') not like '%待审核%');
prepare stmt from @ddl;
execute stmt;
deallocate prepare stmt;

-- 索引决策：is_featured 不建索引。
--   现有约定是「只给 where 里出现过的列建 (列, del_flag) 索引」——category_id / user_id / status
--   都在 selectPostList 的 where 里；is_featured 只做展示、没有查询按它过滤，取值只有 0/1
--   （选择性极低），建了也会被优化器忽略，只增加写入成本。
-- 回填决策：不需要。is_featured 有 default '0'，存量行自动为 '0'；存量 status 值不动。

-- ----------------------------
-- 2、字典：帖子状态补 2 个值（列表页/详情页/表单全部按字典渲染，模板里的取值逻辑不用改）
-- ----------------------------

insert into sys_dict_data (dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status, create_by, create_time, remark)
select 4, '待审核', '3', 'sys_post_status', '', 'warning', 'N', '0', 'admin', sysdate(), '帖子已提交，等待管理员审核，仅作者与审核人可见'
  from dual
 where not exists (select 1 from sys_dict_data where dict_type = 'sys_post_status' and dict_value = '3');

insert into sys_dict_data (dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status, create_by, create_time, remark)
select 5, '已驳回', '4', 'sys_post_status', '', 'danger', 'N', '0', 'admin', sysdate(), '帖子审核未通过，作者可修改后重新提交审核'
  from dual
 where not exists (select 1 from sys_dict_data where dict_type = 'sys_post_status' and dict_value = '4');

-- is_default 保持「草稿」为 'Y'：发帖表单默认仍选中草稿，不改变原有交互。

-- ----------------------------
-- 3、菜单：帖子审核（「学习管理」目录下的 C 菜单）
--    权限 community:post:audit 只授给 admin 角色，覆盖：
--      GET  /community/post/audit            审核列表页（默认筛「待审核」）
--      GET  /community/post/audit/{postId}   审核表单页（弹出层打开）
--      POST /community/post/auditList        审核列表数据
--      POST /community/post/audit            审核保存
--    页面里的「审核」按钮复用同一个权限，不再单插 F 行。
-- ----------------------------

SET @learnMenuId := (SELECT menu_id FROM sys_menu WHERE menu_name = '学习管理' AND parent_id = 0 LIMIT 1);

-- order_num 取同级最大值 +1（沿用 learning_short_video_menu.sql 的写法，不与别人新增的菜单撞号）
SET @auditOrder := (SELECT IFNULL(MAX(order_num), 0) + 1 FROM sys_menu WHERE parent_id = @learnMenuId);

insert into sys_menu (menu_name, parent_id, order_num, url, target, menu_type, visible, is_refresh, perms, icon, create_by, create_time, update_by, update_time, remark)
select '帖子审核', IFNULL(@learnMenuId, 0), @auditOrder, '/community/post/audit', 'menuItem', 'C', '0', '1', 'community:post:audit', '#', 'admin', sysdate(), '', null, '社区帖子审核菜单'
  from dual
 where not exists (select 1 from sys_menu where perms = 'community:post:audit' and menu_type = 'C');

SET @auditMenuId := (SELECT menu_id FROM sys_menu WHERE perms = 'community:post:audit' AND menu_type = 'C' LIMIT 1);

-- 幂等自愈：若历史执行把菜单插成 parent_id=0，这里挂回「学习管理」
-- （@learnMenuId 为 NULL 时 `parent_id <> NULL` 恒为 NULL，匹配不到任何行，安全）
update sys_menu set parent_id = @learnMenuId
 where menu_id = @auditMenuId and parent_id <> @learnMenuId;

-- 角色授权：只授 admin；同时把「学习管理」目录补授给 admin（幂等自愈）。
-- sys_role_menu 主键为 (role_id, menu_id)，insert ignore 保证可重复执行。
insert ignore into sys_role_menu (role_id, menu_id)
select r.role_id, m.menu_id
  from sys_role r
  cross join sys_menu m
 where r.role_key = 'admin'
   and (m.perms = 'community:post:audit'
        or (m.menu_id = @learnMenuId and @learnMenuId is not null));

-- ----------------------------
-- 4、自检
-- ----------------------------

select column_name, column_type, is_nullable, column_default, column_comment
  from information_schema.columns
 where table_schema = database() and table_name = 'learning_post'
   and column_name in ('status', 'is_featured', 'audit_remark')
 order by ordinal_position;
-- 期望：3 行（status 注释含「3待审核 4已驳回」；is_featured char(1) 默认 '0'；audit_remark varchar(500)）

select dict_type, dict_sort, dict_label, dict_value, list_class, is_default, status
  from sys_dict_data
 where dict_type = 'sys_post_status'
 order by dict_sort;
-- 期望：5 行（0已发布 / 1草稿(默认) / 2已下架 / 3待审核(warning) / 4已驳回(danger)）

select menu_id, menu_name, parent_id, order_num, url, menu_type, visible, perms
  from sys_menu
 where perms = 'community:post:audit' or (menu_name = '学习管理' and menu_type = 'M')
 order by parent_id, order_num, menu_id;
-- 期望：帖子审核 1 条，parent_id 指向「学习管理」

select r.role_key, count(1) as audit_menu_count
  from sys_role_menu rm
  join sys_role r on r.role_id = rm.role_id
  join sys_menu m on m.menu_id = rm.menu_id
 where m.perms = 'community:post:audit'
 group by r.role_key;
-- 期望：只有 admin = 1（common 不授权）

select count(*) as featured_null_rows from learning_post where is_featured is null;
-- 期望：0

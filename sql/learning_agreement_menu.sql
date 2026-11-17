-- ----------------------------
-- 平台协议 菜单与按钮权限
--
-- 页面与接口（learning-user 模块已实现）：
--   GET  /user/agreement                协议列表页  → templates/user/agreement/agreement.html
--   GET  /user/agreement/add、/edit/{agreementId}
--   POST /user/agreement/list、add、edit、remove、checkTypeUnique
--
-- 前置条件：先执行过 sql/learning_agreement.sql（建表与三条种子），
--           以及 sql/learning_category_menu.sql（建「学习管理」目录）。
-- 本脚本可重复执行（幂等）。
--
-- 执行方式：
--   mysql --default-character-set=utf8mb4 -h<DB_HOST> -P3306 -u<DB_USER> -p <DB_NAME> < sql/learning_agreement_menu.sql
--
-- 注意：菜单和角色授权在登录时进 Shiro 缓存，执行完需要【重启应用并重新登录】。
--
-- 授权口径与轮播图一致：
--   admin   -> 菜单 + 全部按钮（新增/修改/删除）
--   common  -> 只有菜单入口（view），不能新增/修改/删除
--   查询（list）接口按约定不加权限注解、对所有登录用户开放，所以【不插 :list】。
-- ----------------------------

SET NAMES utf8mb4;

-- ----------------------------
-- 1、菜单与权限
--    菜单 url 指向控制器路径，menu_type：M 目录 / C 菜单 / F 按钮
-- ----------------------------

-- 1.1 复用 learning_category_menu.sql 建好的「学习管理」目录
SET @learnMenuId := (SELECT menu_id FROM sys_menu WHERE menu_name = '学习管理' AND parent_id = 0 LIMIT 1);

-- 1.2 协议管理菜单。order_num 取同级最大值 +1，不硬编码，避免和别人加的菜单撞号
SET @agreementOrder := (SELECT IFNULL(MAX(order_num), 0) + 1 FROM sys_menu WHERE parent_id = @learnMenuId);

insert into sys_menu (menu_name, parent_id, order_num, url, target, menu_type, visible, is_refresh, perms, icon, create_by, create_time, update_by, update_time, remark)
select '协议管理', IFNULL(@learnMenuId, 0), @agreementOrder, '/user/agreement', 'menuItem', 'C', '0', '1', 'user:agreement:view', 'fa fa-file-text-o', 'admin', sysdate(), '', null, '用户协议/隐私政策/关于我们'
  from dual
 where not exists (select 1 from sys_menu where perms = 'user:agreement:view' and menu_type = 'C');

SET @agreementMenuId := (SELECT menu_id FROM sys_menu WHERE perms = 'user:agreement:view' AND menu_type = 'C' LIMIT 1);

-- 1.3 按钮权限：新增 / 修改 / 删除（不插 :list）
insert into sys_menu (menu_name, parent_id, order_num, url, target, menu_type, visible, is_refresh, perms, icon, create_by, create_time, update_by, update_time, remark)
select concat('协议', a.action_label), IFNULL(@agreementMenuId, 0), a.order_num, '#', 'menuItem', 'F', '0', '1',
       concat('user:agreement:', a.action), '#', 'admin', sysdate(), '', null, ''
  from (
        select '新增' as action_label, 'add'    as action, 1 as order_num union all
        select '修改',                'edit',           2 union all
        select '删除',                'remove',         3
       ) a
  left join sys_menu m on m.perms = concat('user:agreement:', a.action)
 where m.menu_id is null;

-- 1.4 把【已存在】的菜单/按钮挂回正确的父节点（历史执行可能插成 parent_id=0）
update sys_menu set parent_id = @learnMenuId
 where menu_id = @agreementMenuId and parent_id <> @learnMenuId;

update sys_menu set parent_id = @agreementMenuId
 where menu_type = 'F' and perms like 'user:agreement:%' and parent_id <> @agreementMenuId;

-- ----------------------------
-- 2、角色授权
--    sys_role_menu 主键为 (role_id, menu_id)，insert ignore 保证可重复执行。
-- ----------------------------

-- 2.1 超级管理员角色：菜单 + 全部按钮（含「学习管理」目录，否则侧边栏没有入口）
insert ignore into sys_role_menu (role_id, menu_id)
select r.role_id, m.menu_id
  from sys_role r
  cross join sys_menu m
 where r.role_key = 'admin'
   and (m.perms like 'user:agreement:%'
        or (m.menu_id = @learnMenuId and @learnMenuId is not null));

-- 2.2 普通角色：只能看
--     先按只读白名单撤销非只读授权（含后续新增的写权限），保证已执行过旧版脚本的库也变为只读
delete rm
  from sys_role_menu rm
  join sys_role r on r.role_id = rm.role_id
  join sys_menu m on m.menu_id = rm.menu_id
 where r.role_key = 'common'
   and m.perms like 'user:agreement:%'
   and m.perms not in ('user:agreement:view');

insert ignore into sys_role_menu (role_id, menu_id)
select r.role_id, m.menu_id
  from sys_role r
  cross join sys_menu m
 where r.role_key = 'common'
   and (m.perms in ('user:agreement:view')
        or (m.menu_id = @learnMenuId and @learnMenuId is not null));

-- ----------------------------
-- 3、自检
-- ----------------------------

select menu_id, menu_name, parent_id, order_num, url, menu_type, perms
  from sys_menu
 where perms like 'user:agreement:%' or (menu_name = '学习管理' and menu_type = 'M')
 order by parent_id, order_num, menu_id;
-- 期望：协议管理菜单 1 条 + 按钮权限 3 条（无 :list），且 parent_id 挂到「学习管理」下

select r.role_key, count(1) as agreement_menu_count
  from sys_role_menu rm
  join sys_role r on r.role_id = rm.role_id
  join sys_menu m on m.menu_id = rm.menu_id
 where m.perms like 'user:agreement:%'
 group by r.role_key;
-- 期望：admin=4（菜单 + 3 个按钮）、common=1（只有菜单 view）

-- 普通角色的非只读协议权限应为 0 条
select r.role_key, m.perms
  from sys_role_menu rm
  join sys_role r on r.role_id = rm.role_id
  join sys_menu m on m.menu_id = rm.menu_id
 where r.role_key = 'common'
   and m.perms like 'user:agreement:%'
   and m.perms not in ('user:agreement:view');

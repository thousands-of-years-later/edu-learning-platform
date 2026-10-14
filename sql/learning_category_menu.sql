-- ----------------------------
-- 类别管理 菜单与按钮权限
--
-- 页面已补齐，可执行。对应的入口（两个控制器里都已实现）：
--   GET /course/category    课程类别列表页  → templates/course/category/category.html
--   GET /course/category/add、/edit/{categoryId}
--   GET /community/category 社区类别列表页  → templates/community/category/category.html
--   GET /community/category/add、/edit/{categoryId}
--
-- 前置条件：先执行过 sql/learning_category.sql（类别表与字典）。
-- 本脚本可重复执行（幂等）。
--
-- 执行方式：
--   mysql --default-character-set=utf8mb4 -h43.142.9.214 -P3306 -uedu_dev_03 -p learning_platform < sql/learning_category_menu.sql
--
-- 注意：菜单和角色授权在登录时进 Shiro 缓存，执行完需要【重启应用并重新登录】。
-- ----------------------------

SET NAMES utf8mb4;

-- ----------------------------
-- 1、菜单与权限
--    菜单 url 指向控制器路径，menu_type：M 目录 / C 菜单 / F 按钮
-- ----------------------------

-- 1.1 一级目录：学习管理
insert into sys_menu (menu_name, parent_id, order_num, url, target, menu_type, visible, is_refresh, perms, icon, create_by, create_time, update_by, update_time, remark)
select '学习管理', 0, 5, '#', 'menuItem', 'M', '0', '1', '', 'fa fa-graduation-cap', 'admin', sysdate(), '', null, '学习平台管理目录'
  from dual
 where not exists (select 1 from sys_menu where menu_name = '学习管理' and parent_id = 0);

-- 说明：不依赖 LAST_INSERT_ID()（上面的 insert 被 not exists 跳过时它会返回旧值），
--       一律按业务键回查菜单ID，保证脚本可重复执行。
SET @learnMenuId     := (SELECT menu_id FROM sys_menu WHERE menu_name = '学习管理' AND parent_id = 0 LIMIT 1);

-- 1.2 课程类别菜单
insert into sys_menu (menu_name, parent_id, order_num, url, target, menu_type, visible, is_refresh, perms, icon, create_by, create_time, update_by, update_time, remark)
select '课程类别', IFNULL(@learnMenuId, 0), 1, '/course/category', 'menuItem', 'C', '0', '1', 'course:category:view', '#', 'admin', sysdate(), '', null, '课程模块类别菜单'
  from dual
 where not exists (select 1 from sys_menu where perms = 'course:category:view' and menu_type = 'C');

-- 1.3 社区类别菜单
insert into sys_menu (menu_name, parent_id, order_num, url, target, menu_type, visible, is_refresh, perms, icon, create_by, create_time, update_by, update_time, remark)
select '社区类别', IFNULL(@learnMenuId, 0), 2, '/community/category', 'menuItem', 'C', '0', '1', 'community:category:view', '#', 'admin', sysdate(), '', null, '社区文章模块类别菜单'
  from dual
 where not exists (select 1 from sys_menu where perms = 'community:category:view' and menu_type = 'C');

SET @courseMenuId    := (SELECT menu_id FROM sys_menu WHERE perms = 'course:category:view'    AND menu_type = 'C' LIMIT 1);
SET @communityMenuId := (SELECT menu_id FROM sys_menu WHERE perms = 'community:category:view' AND menu_type = 'C' LIMIT 1);

-- 1.4 按钮权限：课程类别 / 社区类别 各 5 个（list、add、edit、remove、export）
--     等价于代码生成器 sql.vm 里的 10 条 insert，用反连接一次性幂等写入
insert into sys_menu (menu_name, parent_id, order_num, url, target, menu_type, visible, is_refresh, perms, icon, create_by, create_time, update_by, update_time, remark)
select concat(b.menu_label, a.action_label), IFNULL(b.parent_id, 0), a.order_num, '#', 'menuItem', 'F', '0', '1',
       concat(b.perm_prefix, ':', a.action), '#', 'admin', sysdate(), '', null, ''
  from (
        select '课程类别' as menu_label, @courseMenuId    as parent_id, 'course:category'    as perm_prefix union all
        select '社区类别',              @communityMenuId,               'community:category'
       ) b
  cross join (
        select '查询' as action_label, 'list'   as action, 1 as order_num union all
        select '新增',                'add',          2 union all
        select '修改',                'edit',         3 union all
        select '删除',                'remove',       4 union all
        select '导出',                'export',       5
       ) a
  left join sys_menu m on m.perms = concat(b.perm_prefix, ':', a.action)
 where m.menu_id is null;

-- 1.5 把【已存在】的按钮权限挂到对应的类别菜单下
--     上面 1.4 带 `where m.menu_id is null`，对已存在的权限行会跳过、不会更新 parent_id。
--     早期为了「只授权限、不产生侧边栏死链接」，曾单独插入过这些 F 行（parent_id=0），
--     这里把它们重新挂到各自的类别菜单下，否则在「菜单管理」里会显示成游离的顶级按钮权限。
--     用 perms 前缀精确限定，不碰其他菜单；幂等。
update sys_menu set parent_id = @courseMenuId
 where menu_type = 'F' and perms like 'course:category:%' and parent_id <> @courseMenuId;

update sys_menu set parent_id = @communityMenuId
 where menu_type = 'F' and perms like 'community:category:%' and parent_id <> @communityMenuId;

-- ----------------------------
-- 2、角色授权
--    admin(role_key='admin') 全部放开；common(role_key='common') 仅查看、查询。
--    sys_role_menu 主键为 (role_id, menu_id)，insert ignore 保证可重复执行
-- ----------------------------

insert ignore into sys_role_menu (role_id, menu_id)
select r.role_id, m.menu_id
  from sys_role r
  cross join sys_menu m
 where r.role_key = 'admin'
   and (m.menu_id = @learnMenuId
        or m.perms like 'course:category:%'
        or m.perms like 'community:category:%');

-- 撤销普通角色的历史授权，保证已执行过旧版脚本的数据库也变为只读。
-- 使用只读白名单，同时撤销类别通配权限或后续新增的非只读权限。
delete rm
  from sys_role_menu rm
  join sys_role r on r.role_id = rm.role_id
  join sys_menu m on m.menu_id = rm.menu_id
 where r.role_key = 'common'
   and (m.perms like 'course:category:%' or m.perms like 'community:category:%')
   and m.perms not in ('course:category:view', 'course:category:list',
                      'community:category:view', 'community:category:list');

insert ignore into sys_role_menu (role_id, menu_id)
select r.role_id, m.menu_id
  from sys_role r
  cross join sys_menu m
 where r.role_key = 'common'
   and (m.menu_id = @learnMenuId
        or m.perms in ('course:category:view', 'course:category:list',
                      'community:category:view', 'community:category:list'));

-- ----------------------------
-- 3、自检
-- ----------------------------
select menu_id, menu_name, parent_id, order_num, url, menu_type, perms
  from sys_menu
 where perms like '%:category:%' or (menu_name = '学习管理' and menu_type = 'M')
 order by parent_id, order_num, menu_id;

select r.role_key, count(1) as menu_count
  from sys_role_menu rm
  join sys_role r on r.role_id = rm.role_id
  join sys_menu m on m.menu_id = rm.menu_id
 where m.perms like '%:category:%' or m.menu_name = '学习管理'
 group by r.role_key;
-- 期望：admin=13（1 个目录 + 2 个菜单 + 10 个按钮）、
--       common=5（1 个目录 + 2 个菜单 + 2 个查询按钮）
-- 普通角色的非只读类别权限应为 0 条。
select r.role_key, m.perms
  from sys_role_menu rm
  join sys_role r on r.role_id = rm.role_id
  join sys_menu m on m.menu_id = rm.menu_id
 where r.role_key = 'common'
   and (m.perms like 'course:category:%' or m.perms like 'community:category:%')
   and m.perms not in ('course:category:view', 'course:category:list',
                      'community:category:view', 'community:category:list');

-- ----------------------------
-- 积分商城 菜单与按钮权限
--
-- 页面与接口（shopping 模块已实现）：
--   GET  /shop/category            商品分类列表页  → templates/shop/category/category.html
--   GET  /shop/category/add、/edit/{categoryId}
--   GET  /shop/product             商品管理列表页  → templates/shop/product/product.html
--   GET  /shop/product/add、/edit/{productId}
--   GET  /shop/order               兑换记录列表页  → templates/shop/order/order.html（只读）
--
-- 权限划分：
--   admin  ：目录 + 3 个菜单 + 9 个按钮（全功能）
--   common ：目录 + 商品分类/商品管理的 view、list（只读）
--            兑换记录包含用户和兑换码，不对 common 开放。
--
-- 前置条件：先执行 sql/learning_shop.sql（建表）。
-- 本脚本可重复执行（幂等）。
--
-- 执行方式（共享库执行前先备份）：
--   mysql --default-character-set=utf8mb4 -h<DB_HOST> -P3306 -u<DB_USER> -p <DB_NAME> < sql/learning_shop_menu.sql
--
-- 注意：菜单和角色授权在登录时进 Shiro 缓存，执行完需要【重启应用并重新登录】。
-- ----------------------------

SET NAMES utf8mb4;

-- ----------------------------
-- 1、一级目录：积分商城
--    不硬编码 order_num，取当前顶级菜单最大值 +1，避免和别的目录撞号。
-- ----------------------------
insert into sys_menu (menu_name, parent_id, order_num, url, target, menu_type, visible, is_refresh, perms, icon, create_by, create_time, update_by, update_time, remark)
select '积分商城', 0,
       (select IFNULL(MAX(order_num), 0) + 1 from sys_menu where parent_id = 0),
       '#', 'menuItem', 'M', '0', '1', '', 'fa fa-shopping-bag', 'admin', sysdate(), '', null, '积分商城管理目录'
  from dual
 where not exists (select 1 from sys_menu where menu_name = '积分商城' and parent_id = 0);

SET @shopMenuId := (SELECT menu_id FROM sys_menu WHERE menu_name = '积分商城' AND parent_id = 0 LIMIT 1);

-- ----------------------------
-- 2、三个菜单（C）
-- ----------------------------
insert into sys_menu (menu_name, parent_id, order_num, url, target, menu_type, visible, is_refresh, perms, icon, create_by, create_time, update_by, update_time, remark)
select '商品分类', IFNULL(@shopMenuId, 0), 1, '/shop/category', 'menuItem', 'C', '0', '1',
       'shop:category:view', 'fa fa-tags', 'admin', sysdate(), '', null, '积分商城商品分类'
  from dual
 where not exists (select 1 from sys_menu where perms = 'shop:category:view' and menu_type = 'C');

insert into sys_menu (menu_name, parent_id, order_num, url, target, menu_type, visible, is_refresh, perms, icon, create_by, create_time, update_by, update_time, remark)
select '商品管理', IFNULL(@shopMenuId, 0), 2, '/shop/product', 'menuItem', 'C', '0', '1',
       'shop:product:view', 'fa fa-gift', 'admin', sysdate(), '', null, '积分商城商品管理'
  from dual
 where not exists (select 1 from sys_menu where perms = 'shop:product:view' and menu_type = 'C');

insert into sys_menu (menu_name, parent_id, order_num, url, target, menu_type, visible, is_refresh, perms, icon, create_by, create_time, update_by, update_time, remark)
select '兑换记录', IFNULL(@shopMenuId, 0), 3, '/shop/order', 'menuItem', 'C', '0', '1',
       'shop:order:view', 'fa fa-ticket', 'admin', sysdate(), '', null, '积分商城兑换记录（只读）'
  from dual
 where not exists (select 1 from sys_menu where perms = 'shop:order:view' and menu_type = 'C');

SET @categoryMenuId := (SELECT menu_id FROM sys_menu WHERE perms = 'shop:category:view' AND menu_type = 'C' LIMIT 1);
SET @productMenuId  := (SELECT menu_id FROM sys_menu WHERE perms = 'shop:product:view'  AND menu_type = 'C' LIMIT 1);
SET @orderMenuId    := (SELECT menu_id FROM sys_menu WHERE perms = 'shop:order:view'    AND menu_type = 'C' LIMIT 1);

-- ----------------------------
-- 3、按钮权限（F）
--    分类、商品各 4 个（list/add/edit/remove）；兑换记录只读，只有 list。
-- ----------------------------
insert into sys_menu (menu_name, parent_id, order_num, url, target, menu_type, visible, is_refresh, perms, icon, create_by, create_time, update_by, update_time, remark)
select concat(b.menu_label, a.action_label), b.parent_id, a.order_num, '#', 'menuItem', 'F', '0', '1',
       concat(b.perm_prefix, ':', a.action), '#', 'admin', sysdate(), '', null, ''
  from (
        select '商品分类' as menu_label, @categoryMenuId as parent_id, 'shop:category' as perm_prefix union all
        select '商品管理',              @productMenuId,                 'shop:product'                  union all
        select '兑换记录',              @orderMenuId,                   'shop:order'
       ) b
  cross join (
        select '查询' as action_label, 'list'   as action, 1 as order_num union all
        select '新增',                'add',          2 union all
        select '修改',                'edit',         3 union all
        select '删除',                'remove',       4
       ) a
  left join sys_menu m on m.perms = concat(b.perm_prefix, ':', a.action)
 where m.menu_id is null
   and (b.perm_prefix <> 'shop:order' or a.action = 'list');

-- 把【已存在】的按钮权限挂到对应菜单下（防止历史上被插成 parent_id=0 的游离行）
update sys_menu set parent_id = @categoryMenuId
 where menu_type = 'F' and perms like 'shop:category:%' and parent_id <> @categoryMenuId;

update sys_menu set parent_id = @productMenuId
 where menu_type = 'F' and perms like 'shop:product:%' and parent_id <> @productMenuId;

update sys_menu set parent_id = @orderMenuId
 where menu_type = 'F' and perms like 'shop:order:%' and parent_id <> @orderMenuId;

-- ----------------------------
-- 4、角色授权
--    sys_role_menu 主键为 (role_id, menu_id)，insert ignore 保证可重复执行。
-- ----------------------------
insert ignore into sys_role_menu (role_id, menu_id)
select r.role_id, m.menu_id
  from sys_role r
  cross join sys_menu m
 where r.role_key = 'admin'
   and (m.menu_id = @shopMenuId or m.perms like 'shop:%');

-- common：只读商品分类/商品管理（含目录，否则侧边栏没有入口）
insert ignore into sys_role_menu (role_id, menu_id)
select r.role_id, m.menu_id
  from sys_role r
  cross join sys_menu m
 where r.role_key = 'common'
   and (m.menu_id = @shopMenuId
        or m.perms in ('shop:category:view', 'shop:category:list',
                       'shop:product:view',  'shop:product:list'));

-- 兜底回收：common 不允许保留兑换记录或任何写权限
delete rm
  from sys_role_menu rm
  join sys_role r on r.role_id = rm.role_id
  join sys_menu m on m.menu_id = rm.menu_id
 where r.role_key = 'common'
   and m.perms like 'shop:%'
   and m.perms not in ('shop:category:view', 'shop:category:list',
                       'shop:product:view',  'shop:product:list');

-- ----------------------------
-- 5、自检
-- ----------------------------
select menu_id, menu_name, parent_id, order_num, url, menu_type, perms
  from sys_menu
 where perms like 'shop:%' or (menu_name = '积分商城' and parent_id = 0)
 order by parent_id, order_num, menu_id;
-- 期望：1 个目录 + 3 个菜单 + 9 个按钮（共 13 行）

select r.role_key, count(1) as shop_menu_count
  from sys_role_menu rm
  join sys_role r on r.role_id = rm.role_id
  join sys_menu m on m.menu_id = rm.menu_id
 where m.menu_id = @shopMenuId or m.perms like 'shop:%'
 group by r.role_key;
-- 期望：admin=13、common=5

select r.role_key, m.perms
  from sys_role_menu rm
  join sys_role r on r.role_id = rm.role_id
  join sys_menu m on m.menu_id = rm.menu_id
 where r.role_key = 'common'
   and m.perms like 'shop:%'
   and m.perms not in ('shop:category:view', 'shop:category:list',
                       'shop:product:view',  'shop:product:list');
-- 期望：0 行

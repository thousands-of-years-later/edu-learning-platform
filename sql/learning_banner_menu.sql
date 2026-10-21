-- ----------------------------
-- 课程轮播图管理 菜单与按钮权限
--
-- 页面与接口（learning-course 模块已实现）：
--   GET  /course/banner              轮播图列表页  → templates/course/banner/banner.html
--   GET  /course/banner/add、/edit/{bannerId}
--   POST /course/banner/list、add、edit、remove、export
--   GET  /api/v1/banner/list         App 端只读（Token 认证，不在本脚本授权范围内）
--   GET  /api/banner/list            App 端只读（Shiro 会话）
--
-- 前置条件：先执行过 sql/learning_banner.sql（建表），
--           以及 sql/learning_category_menu.sql（建「学习管理」目录）。
-- 本脚本可重复执行（幂等）。
--
-- 执行方式：
--   mysql --default-character-set=utf8mb4 -h43.142.9.214 -P3306 -uedu_dev_03 -p learning_platform < sql/learning_banner_menu.sql
--
-- 注意：菜单和角色授权在登录时进 Shiro 缓存，执行完需要【重启应用并重新登录】。
--
-- 授权口径就是需求里的「普通用户只能看」：
--   admin   -> 菜单 + 全部按钮
--   common  -> 只有菜单入口（view），add/edit/remove/export 一律不授，
--              并且用只读白名单把历史可能存在的非只读授权删掉。
--   查询（list）接口按约定不加权限注解、对所有登录用户开放，所以【不插 :list】，留着是误导。
-- ----------------------------

SET NAMES utf8mb4;

-- ----------------------------
-- 1、菜单与权限
--    菜单 url 指向控制器路径，menu_type：M 目录 / C 菜单 / F 按钮
-- ----------------------------

-- 1.1 复用 learning_category_menu.sql 建好的「学习管理」目录
--     不依赖 LAST_INSERT_ID()，一律按业务键回查，保证脚本可重复执行
SET @learnMenuId := (SELECT menu_id FROM sys_menu WHERE menu_name = '学习管理' AND parent_id = 0 LIMIT 1);

-- 1.2 轮播图管理菜单。order_num 取同级最大值 +1，不硬编码，避免和别人加的菜单撞号
--     注意 @learnMenuId 为 NULL 时（未执行 learning_category_menu.sql）本级查询返回 NULL，
--     菜单会落到 parent_id=0；脚本末尾的自检查得出来。
SET @bannerOrder := (SELECT IFNULL(MAX(order_num), 0) + 1 FROM sys_menu WHERE parent_id = @learnMenuId);

insert into sys_menu (menu_name, parent_id, order_num, url, target, menu_type, visible, is_refresh, perms, icon, create_by, create_time, update_by, update_time, remark)
select '轮播图管理', IFNULL(@learnMenuId, 0), @bannerOrder, '/course/banner', 'menuItem', 'C', '0', '1', 'course:banner:view', 'fa fa-picture-o', 'admin', sysdate(), '', null, '课程模块轮播图菜单'
  from dual
 where not exists (select 1 from sys_menu where perms = 'course:banner:view' and menu_type = 'C');

SET @bannerMenuId := (SELECT menu_id FROM sys_menu WHERE perms = 'course:banner:view' AND menu_type = 'C' LIMIT 1);

-- 1.3 按钮权限：4 个（add、edit、remove、export）
--     这里【不插 :list】—— 列表接口按约定不校验权限、对所有登录用户开放
--     （遵循 learning_post_menu.sql 里更晚的约定，短视频脚本里插的 :list 是更早的写法）。
insert into sys_menu (menu_name, parent_id, order_num, url, target, menu_type, visible, is_refresh, perms, icon, create_by, create_time, update_by, update_time, remark)
select concat('轮播图管理', a.action_label), IFNULL(@bannerMenuId, 0), a.order_num, '#', 'menuItem', 'F', '0', '1',
       concat('course:banner:', a.action), '#', 'admin', sysdate(), '', null, ''
  from (
        select '新增' as action_label, 'add'   as action, 1 as order_num union all
        select '修改',                'edit',          2 union all
        select '删除',                'remove',        3 union all
        select '导出',                'export',        4
       ) a
  left join sys_menu m on m.perms = concat('course:banner:', a.action)
 where m.menu_id is null;

-- 1.4 把【已存在】的菜单/按钮挂回正确的父节点
--     上面的插入带 `where ... is null`，对已存在的行会跳过、不会更新 parent_id。
--     若历史执行曾把菜单插到 parent_id=0、或把 F 行插成游离的顶级按钮权限，这里重新挂回。
--     @bannerMenuId 为 NULL 时 `parent_id <> NULL` 恒为 NULL，匹配不到任何行，安全。
update sys_menu set parent_id = @learnMenuId
 where menu_id = @bannerMenuId and parent_id <> @learnMenuId;

update sys_menu set parent_id = @bannerMenuId
 where menu_type = 'F' and perms like 'course:banner:%' and parent_id <> @bannerMenuId;

-- ----------------------------
-- 2、角色授权
--    sys_role_menu 主键为 (role_id, menu_id)，insert ignore 保证可重复执行。
-- ----------------------------

-- 2.1 超级管理员角色：菜单 + 全部按钮
--     role_key='admin' 的角色在 RuoYi 原始种子里一条授权都没有
--     （user_id==1 的 admin 由 *:*:* 绕过，不需要授权行），新建的管理员账号要靠这些行才有权限。
insert ignore into sys_role_menu (role_id, menu_id)
select r.role_id, m.menu_id
  from sys_role r
  cross join sys_menu m
 where r.role_key = 'admin'
   and (m.perms like 'course:banner:%'
        or (m.menu_id = @learnMenuId and @learnMenuId is not null));

-- 2.2 普通角色：只能看
--     先按只读白名单撤销非只读授权（含通配权限与后续新增的写权限），保证已执行过旧版脚本的库也变为只读。
delete rm
  from sys_role_menu rm
  join sys_role r on r.role_id = rm.role_id
  join sys_menu m on m.menu_id = rm.menu_id
 where r.role_key = 'common'
   and m.perms like 'course:banner:%'
   and m.perms not in ('course:banner:view');

insert ignore into sys_role_menu (role_id, menu_id)
select r.role_id, m.menu_id
  from sys_role r
  cross join sys_menu m
 where r.role_key = 'common'
   and (m.perms in ('course:banner:view')
        or (m.menu_id = @learnMenuId and @learnMenuId is not null));

-- ----------------------------
-- 3、自检
-- ----------------------------

select menu_id, menu_name, parent_id, order_num, url, menu_type, perms
  from sys_menu
 where perms like 'course:banner:%' or (menu_name = '学习管理' and menu_type = 'M')
 order by parent_id, order_num, menu_id;
-- 期望：轮播图管理菜单 1 条 + 按钮权限 4 条（无 :list），
--       且 parent_id 挂到「学习管理」下（不是 0）

select r.role_key, count(1) as banner_menu_count
  from sys_role_menu rm
  join sys_role r on r.role_id = rm.role_id
  join sys_menu m on m.menu_id = rm.menu_id
 where m.perms like 'course:banner:%'
 group by r.role_key;
-- 期望：admin=5（菜单 + 4 个按钮）、common=1（只有菜单 view）

-- 普通角色的非只读轮播权限应为 0 条
select r.role_key, m.perms
  from sys_role_menu rm
  join sys_role r on r.role_id = rm.role_id
  join sys_menu m on m.menu_id = rm.menu_id
 where r.role_key = 'common'
   and m.perms like 'course:banner:%'
   and m.perms not in ('course:banner:view');

-- ----------------------------
-- 短视频管理 菜单与按钮权限
--
-- 页面与接口（learning-video 模块已实现）：
--   GET  /video/shortvideo            视频列表页  → templates/video/shortvideo/shortvideo.html
--   GET  /video/shortvideo/add        新增页
--   GET  /video/shortvideo/edit/{id}  编辑页
--   POST /video/shortvideo/list       列表数据（不加权限注解，所有登录用户可读）
--
-- 前置条件：先执行过 sql/learning_short_video.sql（建表）。
-- 「学习管理」目录由 sql/learning_category_menu.sql 创建，本脚本只往里挂菜单。
-- 本脚本可重复执行（幂等）。
--
-- 执行方式：
--   mysql --default-character-set=utf8mb4 -h43.142.9.214 -P3306 -uedu_dev_03 -p learning_platform < sql/learning_short_video_menu.sql
--
-- 注意：菜单和角色授权在登录时进 Shiro 缓存，执行完需要【重启应用并重新登录】。
-- ----------------------------

SET NAMES utf8mb4;

-- ----------------------------
-- 1、菜单与权限
--    菜单 url 指向控制器路径，menu_type：M 目录 / C 菜单 / F 按钮
-- ----------------------------

-- 1.1 复用 learning_category_menu.sql 建好的「学习管理」目录
--     不依赖 LAST_INSERT_ID()，一律按业务键回查，保证脚本可重复执行
SET @learnMenuId := (SELECT menu_id FROM sys_menu WHERE menu_name = '学习管理' AND parent_id = 0 LIMIT 1);

-- 1.2 短视频管理菜单。order_num 取同级最大值 +1，不硬编码，避免和别人加的菜单撞号
SET @videoOrder := (SELECT IFNULL(MAX(order_num), 0) + 1 FROM sys_menu WHERE parent_id = @learnMenuId);

insert into sys_menu (menu_name, parent_id, order_num, url, target, menu_type, visible, is_refresh, perms, icon, create_by, create_time, update_by, update_time, remark)
select '短视频管理', IFNULL(@learnMenuId, 0), @videoOrder, '/video/shortvideo', 'menuItem', 'C', '0', '1', 'video:shortvideo:view', 'fa fa-video-camera', 'admin', sysdate(), '', null, '短视频模块菜单'
  from dual
 where not exists (select 1 from sys_menu where perms = 'video:shortvideo:view' and menu_type = 'C');

SET @videoMenuId := (SELECT menu_id FROM sys_menu WHERE perms = 'video:shortvideo:view' AND menu_type = 'C' LIMIT 1);

-- 1.3 按钮权限：5 个（list、add、edit、remove、export）
insert into sys_menu (menu_name, parent_id, order_num, url, target, menu_type, visible, is_refresh, perms, icon, create_by, create_time, update_by, update_time, remark)
select concat('短视频管理', a.action_label), IFNULL(@videoMenuId, 0), a.order_num, '#', 'menuItem', 'F', '0', '1',
       concat('video:shortvideo:', a.action), '#', 'admin', sysdate(), '', null, ''
  from (
        select '查询' as action_label, 'list'   as action, 1 as order_num union all
        select '新增',                'add',          2 union all
        select '修改',                'edit',         3 union all
        select '删除',                'remove',       4 union all
        select '导出',                'export',       5
       ) a
  left join sys_menu m on m.perms = concat('video:shortvideo:', a.action)
 where m.menu_id is null;

-- 1.4 把【已存在】的按钮权限挂到短视频管理菜单下
--     上面的插入带 `where m.menu_id is null`，对已存在的权限行会跳过、不会更新 parent_id。
--     若历史执行曾把 F 行插成 parent_id=0，这里重新挂回，避免「菜单管理」里显示成游离的顶级按钮权限。
update sys_menu set parent_id = @videoMenuId
 where menu_type = 'F' and perms like 'video:shortvideo:%' and parent_id <> @videoMenuId;

-- ----------------------------
-- 2、角色授权
--    sys_role_menu 主键为 (role_id, menu_id)，insert ignore 保证可重复执行。
--
--    与课程/帖子一致：
--      · 菜单入口(view) + 查询(list)  -> admin 与 common 都放开
--      · 增、改、删、导出            -> 只给 admin
-- ----------------------------

insert ignore into sys_role_menu (role_id, menu_id)
select r.role_id, m.menu_id
  from sys_role r
  cross join sys_menu m
 where r.role_key in ('admin', 'common')
   and (m.perms in ('video:shortvideo:view', 'video:shortvideo:list')
        or (m.menu_id = @learnMenuId and @learnMenuId is not null));

insert ignore into sys_role_menu (role_id, menu_id)
select r.role_id, m.menu_id
  from sys_role r
  cross join sys_menu m
 where r.role_key = 'admin'
   and m.perms in ('video:shortvideo:add', 'video:shortvideo:edit', 'video:shortvideo:remove', 'video:shortvideo:export');

-- ----------------------------
-- 3、自检
-- ----------------------------

select menu_id, menu_name, parent_id, order_num, url, menu_type, perms
  from sys_menu
 where perms like 'video:shortvideo:%' or (menu_name = '学习管理' and menu_type = 'M')
 order by parent_id, order_num, menu_id;

select r.role_key, count(1) as video_menu_count
  from sys_role_menu rm
  join sys_role r on r.role_id = rm.role_id
  join sys_menu m on m.menu_id = rm.menu_id
 where m.perms like 'video:shortvideo:%'
 group by r.role_key;
-- 期望：短视频管理菜单 1 条 + 按钮权限 5 条；
--       授权 admin=6（菜单+5 个按钮）、common=1（只有菜单 + list 共 2 条，按 perms 统计为 2）
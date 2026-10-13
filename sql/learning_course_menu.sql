-- ----------------------------
-- 课程管理 菜单与按钮权限
--
-- 页面与接口（learning-course 模块已实现）：
--   GET /course/course    课程列表页  → templates/course/course/course.html
--   GET /course/course/add、/edit/{courseId}
--   GET /course/video     某课程的视频列表页（?courseId=）
--   GET /course/chapter   某课程的章节列表页（?courseId=）
--
-- 前置条件：先执行过 sql/learning_course.sql（建表）与 sql/learning_category_menu.sql（建「学习管理」目录）。
-- 本脚本可重复执行（幂等）。
--
-- 执行方式：
--   mysql --default-character-set=utf8mb4 -h43.142.9.214 -P3306 -uedu_dev_03 -p learning_platform < sql/learning_course_menu.sql
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

-- 1.2 课程管理菜单（order_num=3，1、2 已被课程类别/社区类别占用）
insert into sys_menu (menu_name, parent_id, order_num, url, target, menu_type, visible, is_refresh, perms, icon, create_by, create_time, update_by, update_time, remark)
select '课程管理', IFNULL(@learnMenuId, 0), 3, '/course/course', 'menuItem', 'C', '0', '1', 'course:course:view', '#', 'admin', sysdate(), '', null, '课程模块菜单'
  from dual
 where not exists (select 1 from sys_menu where perms = 'course:course:view' and menu_type = 'C');

SET @courseMenuId := (SELECT menu_id FROM sys_menu WHERE perms = 'course:course:view' AND menu_type = 'C' LIMIT 1);

-- 1.3 按钮权限：课程管理 5 个（list、add、edit、remove、export）
--     视频、章节的增删改复用同一套权限，不单开 course:video:* / course:chapter:*
insert into sys_menu (menu_name, parent_id, order_num, url, target, menu_type, visible, is_refresh, perms, icon, create_by, create_time, update_by, update_time, remark)
select concat('课程管理', a.action_label), IFNULL(@courseMenuId, 0), a.order_num, '#', 'menuItem', 'F', '0', '1',
       concat('course:course:', a.action), '#', 'admin', sysdate(), '', null, ''
  from (
        select '查询' as action_label, 'list'   as action, 1 as order_num union all
        select '新增',                'add',          2 union all
        select '修改',                'edit',         3 union all
        select '删除',                'remove',       4 union all
        select '导出',                'export',       5
       ) a
  left join sys_menu m on m.perms = concat('course:course:', a.action)
 where m.menu_id is null;

-- 1.4 把【已存在】的按钮权限挂到课程管理菜单下
--     上面的插入带 `where m.menu_id is null`，对已存在的权限行会跳过、不会更新 parent_id。
--     若历史执行曾把 F 行插成 parent_id=0，这里重新挂回，避免「菜单管理」里显示成游离的顶级按钮权限。
--     @courseMenuId 为 NULL 时 `parent_id <> NULL` 恒为 NULL，匹配不到任何行，安全。
update sys_menu set parent_id = @courseMenuId
 where menu_type = 'F' and perms like 'course:course:%' and parent_id <> @courseMenuId;

-- ----------------------------
-- 2、角色授权
--    sys_role_menu 主键为 (role_id, menu_id)，insert ignore 保证可重复执行。
--
--    这里是「增删改只能是管理员，用户可以查」的落点：
--      · 菜单入口(view) + 查询(list)  -> admin 与 common 都放开，普通用户能进页面只读浏览
--      · 增、改、删、导出            -> 只给 admin
--    另外控制器里对所有写接口还有一道 ShiroUtils.isAdmin()（user_id==1）校验兜底。
-- ----------------------------

insert ignore into sys_role_menu (role_id, menu_id)
select r.role_id, m.menu_id
  from sys_role r
  cross join sys_menu m
 where r.role_key in ('admin', 'common')
   and (m.perms in ('course:course:view', 'course:course:list')
        or (m.menu_id = @learnMenuId and @learnMenuId is not null));

insert ignore into sys_role_menu (role_id, menu_id)
select r.role_id, m.menu_id
  from sys_role r
  cross join sys_menu m
 where r.role_key = 'admin'
   and m.perms in ('course:course:add', 'course:course:edit', 'course:course:remove', 'course:course:export');

-- ----------------------------
-- 3、自检
-- ----------------------------
select menu_id, menu_name, parent_id, order_num, url, menu_type, perms
  from sys_menu
 where perms like 'course:course:%' or (menu_name = '学习管理' and menu_type = 'M')
 order by parent_id, order_num, menu_id;

select r.role_key, count(1) as course_menu_count
  from sys_role_menu rm
  join sys_role r on r.role_id = rm.role_id
  join sys_menu m on m.menu_id = rm.menu_id
 where m.perms like 'course:course:%'
 group by r.role_key;
-- 期望：课程管理菜单 1 条 + 按钮权限 5 条；
--       授权 admin=6（菜单+5 个按钮）、common=2（菜单 + list）

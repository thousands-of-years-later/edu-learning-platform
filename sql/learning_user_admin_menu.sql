-- ----------------------------
-- 评价管理 / 签到记录 / 积分账户 / 积分流水 菜单与按钮权限
--
-- 页面与接口：
--   GET  /course/rating              评价管理页   → templates/course/rating/rating.html
--   POST /course/rating/list、remove                    (course:rating)
--   GET  /user/checkin               签到记录页   → templates/user/checkin/checkin.html
--   POST /user/checkin/list                             (user:checkin)
--   GET  /user/points/account        积分账户页   → templates/user/points/account.html
--   GET  /user/points/log            积分流水页   → templates/user/points/log.html
--   POST /user/points/account/list、log/list            (user:points)
--
-- 前置条件：sql/learning_course_rating.sql、learning_checkin.sql 已执行；
--           与 sql/learning_category_menu.sql（建「学习管理」目录）。
-- 本脚本可重复执行（幂等）。
--
-- 执行方式：
--   mysql --default-character-set=utf8mb4 -h43.142.9.214 -P3306 -uedu_dev_03 -p learning_platform < sql/learning_user_admin_menu.sql
--
-- 注意：菜单和角色授权在登录时进 Shiro 缓存，执行完需要【重启应用并重新登录】。
--
-- 授权口径：都是纯管理/审计页面，只授给 admin。
-- ----------------------------

SET NAMES utf8mb4;

SET @learnMenuId := (SELECT menu_id FROM sys_menu WHERE menu_name = '学习管理' AND parent_id = 0 LIMIT 1);

-- ----------------------------
-- 1、评价管理（course:rating）
-- ----------------------------
SET @ratingOrder := (SELECT IFNULL(MAX(order_num), 0) + 1 FROM sys_menu WHERE parent_id = @learnMenuId);

insert into sys_menu (menu_name, parent_id, order_num, url, target, menu_type, visible, is_refresh, perms, icon, create_by, create_time, update_by, update_time, remark)
select '评价管理', IFNULL(@learnMenuId, 0), @ratingOrder, '/course/rating', 'menuItem', 'C', '0', '1', 'course:rating:view', 'fa fa-star', 'admin', sysdate(), '', null, '课程评价管理菜单'
  from dual
 where not exists (select 1 from sys_menu where perms = 'course:rating:view' and menu_type = 'C');

SET @ratingMenuId := (SELECT menu_id FROM sys_menu WHERE perms = 'course:rating:view' AND menu_type = 'C' LIMIT 1);

insert into sys_menu (menu_name, parent_id, order_num, url, target, menu_type, visible, is_refresh, perms, icon, create_by, create_time, update_by, update_time, remark)
select concat('评价', a.action_label), IFNULL(@ratingMenuId, 0), a.order_num, '#', 'menuItem', 'F', '0', '1',
       concat('course:rating:', a.action), '#', 'admin', sysdate(), '', null, ''
  from (
        select '列表' as action_label, 'list'   as action, 1 as order_num union all
        select '删除',                'remove',        2
       ) a
  left join sys_menu m on m.perms = concat('course:rating:', a.action)
 where m.menu_id is null;

update sys_menu set parent_id = @ratingMenuId
 where menu_type = 'F' and perms like 'course:rating:%' and parent_id <> @ratingMenuId;

-- ----------------------------
-- 2、签到记录（user:checkin）
-- ----------------------------
SET @checkinOrder := (SELECT IFNULL(MAX(order_num), 0) + 1 FROM sys_menu WHERE parent_id = @learnMenuId);

insert into sys_menu (menu_name, parent_id, order_num, url, target, menu_type, visible, is_refresh, perms, icon, create_by, create_time, update_by, update_time, remark)
select '签到记录', IFNULL(@learnMenuId, 0), @checkinOrder, '/user/checkin', 'menuItem', 'C', '0', '1', 'user:checkin:view', 'fa fa-calendar-check-o', 'admin', sysdate(), '', null, '签到记录菜单'
  from dual
 where not exists (select 1 from sys_menu where perms = 'user:checkin:view' and menu_type = 'C');

SET @checkinMenuId := (SELECT menu_id FROM sys_menu WHERE perms = 'user:checkin:view' AND menu_type = 'C' LIMIT 1);

insert into sys_menu (menu_name, parent_id, order_num, url, target, menu_type, visible, is_refresh, perms, icon, create_by, create_time, update_by, update_time, remark)
select '签到列表', IFNULL(@checkinMenuId, 0), 1, '#', 'menuItem', 'F', '0', '1', 'user:checkin:list', '#', 'admin', sysdate(), '', null, ''
  from dual
 where not exists (select 1 from sys_menu where perms = 'user:checkin:list' and menu_type = 'F');

update sys_menu set parent_id = @checkinMenuId
 where menu_type = 'F' and perms like 'user:checkin:%' and parent_id <> @checkinMenuId;

-- ----------------------------
-- 3、积分账户 + 积分流水（user:points）
--     一个菜单下挂两个页面：账户页里放了去流水的按钮，流水页放回去的按钮。
--     这里用「积分账户」作为主菜单，「积分流水」作为它的按钮级入口（url 指向页面）。
-- ----------------------------
SET @pointsOrder := (SELECT IFNULL(MAX(order_num), 0) + 1 FROM sys_menu WHERE parent_id = @learnMenuId);

insert into sys_menu (menu_name, parent_id, order_num, url, target, menu_type, visible, is_refresh, perms, icon, create_by, create_time, update_by, update_time, remark)
select '积分账户', IFNULL(@learnMenuId, 0), @pointsOrder, '/user/points/account', 'menuItem', 'C', '0', '1', 'user:points:view', 'fa fa-diamond', 'admin', sysdate(), '', null, '积分账户菜单'
  from dual
 where not exists (select 1 from sys_menu where perms = 'user:points:view' and menu_type = 'C');

SET @pointsMenuId := (SELECT menu_id FROM sys_menu WHERE perms = 'user:points:view' AND menu_type = 'C' LIMIT 1);

insert into sys_menu (menu_name, parent_id, order_num, url, target, menu_type, visible, is_refresh, perms, icon, create_by, create_time, update_by, update_time, remark)
select '积分列表', IFNULL(@pointsMenuId, 0), 1, '#', 'menuItem', 'F', '0', '1', 'user:points:list', '#', 'admin', sysdate(), '', null, ''
  from dual
 where not exists (select 1 from sys_menu where perms = 'user:points:list' and menu_type = 'F');

update sys_menu set parent_id = @pointsMenuId
 where menu_type = 'F' and perms like 'user:points:%' and parent_id <> @pointsMenuId;

-- 积分流水页面单独一个菜单项（菜单管理里可见，指向 /user/points/log）
insert into sys_menu (menu_name, parent_id, order_num, url, target, menu_type, visible, is_refresh, perms, icon, create_by, create_time, update_by, update_time, remark)
select '积分流水', @pointsMenuId, 3, '/user/points/log', 'menuItem', 'C', '1', '1', 'user:points:log', 'fa fa-list-alt', 'admin', sysdate(), '', null, '积分流水页面（隐藏菜单，从积分账户页进入）'
  from dual
 where not exists (select 1 from sys_menu where perms = 'user:points:log' and url = '/user/points/log');

-- ----------------------------
-- 4、角色授权（只给 admin）
-- ----------------------------
insert ignore into sys_role_menu (role_id, menu_id)
select r.role_id, m.menu_id
  from sys_role r
  cross join sys_menu m
 where r.role_key = 'admin'
   and (m.perms in ('course:rating:view', 'course:rating:list', 'course:rating:remove',
                    'user:checkin:view', 'user:checkin:list',
                    'user:points:view', 'user:points:list', 'user:points:log')
        or (m.menu_id = @learnMenuId and @learnMenuId is not null));

-- ----------------------------
-- 5、自检
-- ----------------------------
select menu_id, menu_name, parent_id, order_num, url, menu_type, visible, perms
  from sys_menu
 where perms like 'course:rating:%' or perms like 'user:checkin:%' or perms like 'user:points:%'
 order by parent_id, order_num, menu_id;

select r.role_key, count(1) as cnt
  from sys_role_menu rm
  join sys_role r on r.role_id = rm.role_id
  join sys_menu m on m.menu_id = rm.menu_id
 where m.perms like 'course:rating:%' or m.perms like 'user:checkin:%' or m.perms like 'user:points:%'
 group by r.role_key;
-- 期望：评价菜单 1 + 按钮 2；签到菜单 1 + 按钮 1；积分菜单 2 + 按钮 2；admin 全部授权

-- ----------------------------
-- 后台菜单调整：删除「签到记录」，把「积分账户」改为「积分记录」
--
-- 背景：
--   1) 签到记录页取消（签到数据仍保留，只是后台不再单独展示；签到产生的积分会出现在积分明细里）；
--   2) 积分账户/积分流水两个页面合并成「积分记录」：
--      列表 = 每个用户一行（总积分、累计获得、最后变动），
--      点「查看明细」= 这个用户的每一条积分来源 + 积分变动 + 变动后余额。
--
-- 积分来源的扩展方式（代码侧）：
--   来源编码存在 learning_points_log.type，中文名在 PointsSource 里维护；
--   以后加新来源（任务、邀请、活动…）只需在 PointsSource 加常量+标签，发积分时写一条流水，
--   本页会自动出现新来源，不需要再改菜单或页面。
--
-- 本脚本可重复执行（幂等）。
-- 执行顺序：在 learning_user_admin_menu.sql 之后执行（那个脚本会创建签到/积分账户菜单）。
--
-- 执行方式：
--   mysql --default-character-set=utf8mb4 -h43.142.9.214 -P3306 -uedu_dev_03 -p learning_platform < sql/learning_zz_points_records_menu.sql
--
-- 注意：菜单与角色授权在登录时进 Shiro 缓存，执行完需要【重启应用并重新登录】。
-- ----------------------------

SET NAMES utf8mb4;

SET @learnMenuId := (select menu_id from sys_menu where menu_name = '学习管理' and parent_id = 0 limit 1);

-- ----------------------------
-- 1、删除「签到记录」菜单与按钮
-- ----------------------------
delete rm
  from sys_role_menu rm
  join sys_menu m on m.menu_id = rm.menu_id
 where m.perms like 'user:checkin:%';

delete from sys_menu where perms like 'user:checkin:%';

-- ----------------------------
-- 2、删除旧的「积分账户 / 积分流水」菜单与按钮
--    积分账户菜单的 url 是 /user/points/account，积分流水是 /user/points/log（隐藏菜单）
-- ----------------------------
delete rm
  from sys_role_menu rm
  join sys_menu m on m.menu_id = rm.menu_id
 where m.url in ('/user/points/account', '/user/points/log')
    or m.perms = 'user:points:log';

delete from sys_menu
 where url in ('/user/points/account', '/user/points/log')
    or perms = 'user:points:log';

-- 旧按钮「积分列表」的 parent 指向已删菜单，先记下来，后面挂到新菜单下
update sys_menu set parent_id = 0 where menu_type = 'F' and perms = 'user:points:list';

-- ----------------------------
-- 3、创建（或修正）「积分记录」菜单：/user/points/records，perms user:points:view
-- ----------------------------
set @pointsMenuId := (select menu_id from sys_menu where perms = 'user:points:view' and menu_type = 'C' limit 1);

insert into sys_menu (menu_name, parent_id, order_num, url, target, menu_type, visible, is_refresh, perms, icon, create_by, create_time, update_by, update_time, remark)
select '积分记录', IFNULL(@learnMenuId, 0),
       (select IFNULL(MAX(order_num), 0) + 1 from sys_menu where parent_id = @learnMenuId),
       '/user/points/records', 'menuItem', 'C', '0', '1', 'user:points:view', 'fa fa-diamond',
       'admin', sysdate(), '', null, '积分记录（按用户查看积分来源与明细）'
  from dual
 where not exists (select 1 from sys_menu where perms = 'user:points:view' and menu_type = 'C');

-- 已存在时修正名称与地址（旧数据叫「积分账户」、地址是 /user/points/account）
update sys_menu
   set menu_name = '积分记录',
       url = '/user/points/records',
       remark = '积分记录（按用户查看积分来源与明细）'
 where perms = 'user:points:view' and menu_type = 'C';

set @pointsMenuId := (select menu_id from sys_menu where perms = 'user:points:view' and menu_type = 'C' limit 1);

-- 列表按钮挂到「积分记录」菜单下
insert into sys_menu (menu_name, parent_id, order_num, url, target, menu_type, visible, is_refresh, perms, icon, create_by, create_time, update_by, update_time, remark)
select '积分列表', IFNULL(@pointsMenuId, 0), 1, '#', 'menuItem', 'F', '0', '1', 'user:points:list', '#', 'admin', sysdate(), '', null, ''
  from dual
 where not exists (select 1 from sys_menu where perms = 'user:points:list' and menu_type = 'F');

update sys_menu set parent_id = @pointsMenuId
 where menu_type = 'F' and perms = 'user:points:list' and parent_id <> @pointsMenuId;

-- ----------------------------
-- 4、角色授权（只给 admin）
-- ----------------------------
insert ignore into sys_role_menu (role_id, menu_id)
select r.role_id, m.menu_id
  from sys_role r
  cross join sys_menu m
 where r.role_key = 'admin'
   and (m.perms in ('user:points:view', 'user:points:list')
        or (m.menu_id = @learnMenuId and @learnMenuId is not null));

-- ----------------------------
-- 5、菜单序号重排 1..N
-- ----------------------------
update sys_menu m
  join (
        select menu_id, row_number() over (order by order_num, menu_id) as rn
          from sys_menu
         where parent_id = @learnMenuId and menu_type = 'C'
       ) t on t.menu_id = m.menu_id
   set m.order_num = t.rn;

-- ----------------------------
-- 6、自检：期望「学习管理」下只剩 8 个菜单，且包含「积分记录」、没有「签到记录」
-- ----------------------------
select menu_id, menu_name, order_num, url, menu_type, perms
  from sys_menu
 where parent_id = @learnMenuId
 order by order_num, menu_id;

select count(*) as 签到菜单残留 from sys_menu where perms like 'user:checkin:%';
select count(*) as 旧积分菜单残留 from sys_menu where url in ('/user/points/account', '/user/points/log');

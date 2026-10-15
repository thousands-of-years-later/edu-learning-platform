-- ----------------------------
-- 新建平台管理员账号 + 补齐「超级管理员」角色的菜单授权
--
-- 背景：原来的 ry 账号（user_id=2，普通角色）已删除，不再恢复，改为新建一个管理员账号。
--
-- 关键前提（务必理解，否则会踩坑）：
--   RuoYi 4.8.3 判定「管理员」靠的是 **user_id == 1**（ShiroUtils.isAdmin），不是 role_key。
--   user_id==1 的 admin 由 UserRealm 直接授予 *:*:* 和 admin 角色，所以原始种子里
--   **role 1「超级管理员」在 sys_role_menu 里一条授权都没有**。
--   因此，任何新账号即使挂上 role 1，如果不补授权，登录后也是一点权限都没有。
--   本脚本第 1 节就是把 role 2（普通角色，已有全部菜单）的授权复制给 role 1，让它变成真正可用的超级管理员角色。
--
-- 账号信息：
--   登录名 eduadmin / 手机号 13800000000 / 密码 admin123
--   密码哈希算法与 SysPasswordService.encryptPassword 一致：MD5(loginName + password + salt)
--   已用 admin 的哈希反推校验过：MD5('admin'+'admin123'+'111111') = 29c67a30398638269fe600f73a054934，与库中一致。
--
-- 前置条件：先执行过 sql/learning_category.sql（类别表、字典、类别接口权限）。
-- 本脚本可重复执行（幂等）。
--
-- 执行方式：
--   mysql --default-character-set=utf8mb4 -h43.142.9.214 -P3306 -uedu_dev_03 -p learning_platform < sql/learning_admin_account.sql
--
-- 注意：授权信息在登录时进 Shiro 缓存，执行完需要【重启应用并重新登录】。
--      密码 admin123 是弱密码，首次登录后请在「个人中心」立即修改。
-- ----------------------------

SET NAMES utf8mb4;

-- ----------------------------
-- 1、补齐「超级管理员」(role_key='admin') 的菜单授权
--    从 role_key='common' 复制现有授权（该角色已有全部菜单），
--    sys_role_menu 主键为 (role_id, menu_id)，insert ignore 保证可重复执行。
-- ----------------------------

-- 注意：这里【不再】从 common 复制 —— common 是业务只读角色（见
-- sql/learning_fix_role_permissions.sql），拿它当基准会让 admin 权限不足。
-- 直接把全部菜单授予 admin。
insert ignore into sys_role_menu (role_id, menu_id)
select (select role_id from sys_role where role_key = 'admin' limit 1),
       m.menu_id
  from sys_menu m
 where (select role_id from sys_role where role_key = 'admin' limit 1) is not null;

-- 管理员必须是「全部数据」范围，否则 user_id != 1 的账号看不到全部用户
update sys_role set data_scope = '1' where role_key = 'admin';

-- ----------------------------
-- 2、新建管理员账号
--    sys_user 里除 login_name 外都有默认值，这里显式写全便于核对。
-- ----------------------------

insert into sys_user (dept_id, login_name, user_name, user_type, email, phonenumber, sex, avatar,
                      password, salt, status, del_flag, create_by, create_time, remark)
select 103, 'eduadmin', '平台管理员', '00', 'eduadmin@qq.com', '13800000000', '0', '',
       '262c5046bcbd8b46eb9ae5f9e8539e6d', '111111', '0', '0', 'admin', sysdate(), '平台管理员账号'
  from dual
 where not exists (select 1 from sys_user where login_name = 'eduadmin');

-- ----------------------------
-- 3、绑定「超级管理员」角色
-- ----------------------------

insert ignore into sys_user_role (user_id, role_id)
select u.user_id, (select role_id from sys_role where role_key = 'admin' limit 1)
  from sys_user u
 where u.login_name = 'eduadmin'
   and (select role_id from sys_role where role_key = 'admin' limit 1) is not null;

-- ----------------------------
-- 4、自检
-- ----------------------------
select u.user_id, u.login_name, u.user_name, u.phonenumber, u.status, u.del_flag, r.role_key
  from sys_user u
       left join sys_user_role ur on ur.user_id = u.user_id
       left join sys_role r on r.role_id = ur.role_id
 where u.login_name = 'eduadmin';

select r.role_key, count(1) as menu_count
  from sys_role_menu rm
  join sys_role r on r.role_id = rm.role_id
 group by r.role_key
 order by r.role_key;
-- 期望：eduadmin 挂 admin 角色；admin 角色授权数 = common 角色授权数 + 类别的 8 条写操作权限

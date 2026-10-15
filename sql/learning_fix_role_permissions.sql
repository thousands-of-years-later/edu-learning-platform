-- ----------------------------
-- 修复角色权限：普通角色(common)不该有系统管理权限
--
-- 背景（安全问题）：
--   若依原始种子 ry_20260319.sql 把「普通角色」(role_key='common') 授成了几乎全权限
--   （system:user:add/edit/remove/resetPwd、system:role:*、monitor:*、tool:* 全都有）。
--   它是官方的演示角色，设计上就是"什么都能干"，留在生产里等于：
--     任何一个挂 common 角色的账号，都能删用户、改别人密码、改角色、解锁账号。
--
--   另外 sql/learning_admin_account.sql 第 1 节当时是「从 common 复制授权给 admin」，
--   等于把这套过大权限当成了基准，本脚本一并纠正（该文件已同步修改）。
--
-- 本脚本做三件事：
--   1. admin 角色：显式授予【全部菜单】，不再依赖 common
--   2. common 角色：撤销所有 system: / monitor: / tool: 权限，只保留业务（学习管理）菜单
--   3. common 角色的数据范围改成「仅本人」(data_scope='5')，避免看到全部用户
--
-- 本脚本可重复执行（幂等）。
--
-- 执行方式：
--   mysql --default-character-set=utf8mb4 -h43.142.9.214 -P3306 -uedu_dev_03 -p learning_platform < sql/learning_fix_role_permissions.sql
--
-- 注意：权限在登录时进 Shiro 缓存，执行完需要【重启应用并重新登录】。
-- ----------------------------

SET NAMES utf8mb4;

-- ----------------------------
-- 1、admin 角色：显式授予全部菜单
--    不再从 common 复制 —— 那条路会让 admin 的权限随 common 的调整而漂移
-- ----------------------------
insert ignore into sys_role_menu (role_id, menu_id)
select (select role_id from sys_role where role_key = 'admin' limit 1), m.menu_id
  from sys_menu m
 where (select role_id from sys_role where role_key = 'admin' limit 1) is not null;

-- ----------------------------
-- 2、common 角色：撤销系统管理 / 监控 / 工具的权限
--    只保留业务（学习管理：课程类别、社区类别、课程管理、社区帖子、短视频管理）的只读权限
-- ----------------------------
delete rm from sys_role_menu rm
  join sys_role r on r.role_id = rm.role_id
  join sys_menu m on m.menu_id = rm.menu_id
 where r.role_key = 'common'
   and (m.perms like 'system:%' or m.perms like 'monitor:%' or m.perms like 'tool:%');

-- 兜底：按菜单名再撤一次（有些历史种子行的 perms 为空，靠 perms 匹配不到）
delete rm from sys_role_menu rm
  join sys_role r on r.role_id = rm.role_id
  join sys_menu m on m.menu_id = rm.menu_id
 where r.role_key = 'common'
   and (m.menu_name like '系统管理%' or m.menu_name like '系统监控%' or m.menu_name like '系统工具%'
     or m.menu_name in ('用户管理','角色管理','菜单管理','部门管理','岗位管理','字典管理','参数设置',
                        '通知公告','日志管理','在线用户','定时任务','数据监控','服务监控','缓存监控',
                        '表单构建','代码生成','系统接口')
     or m.menu_name like '用户%' or m.menu_name like '角色%' or m.menu_name like '菜单%'
     or m.menu_name like '部门%' or m.menu_name like '岗位%' or m.menu_name like '字典%'
     or m.menu_name like '参数%' or m.menu_name like '任务%' or m.menu_name like '操作%'
     or m.menu_name like '登录%' or m.menu_name like '生成%' or m.menu_name like '公告%');

-- ----------------------------
-- 3、common 角色的数据范围：改为「仅本人」
--    1=全部 2=自定义 3=本部门 4=本部门及以下 5=仅本人
-- ----------------------------
update sys_role set data_scope = '5' where role_key = 'common';

-- admin 角色务必是「全部」，否则 user_id != 1 的管理员（如 eduadmin）会看不到全部用户
update sys_role set data_scope = '1' where role_key = 'admin';

-- ----------------------------
-- 4、自检
-- ----------------------------

-- 4.1 common 还剩哪些权限（期望：只剩学习管理的菜单和 list）
select r.role_key, m.perms, m.menu_name
  from sys_role r
  join sys_role_menu rm on rm.role_id = r.role_id
  join sys_menu m on m.menu_id = rm.menu_id
 where r.role_key = 'common'
 order by m.perms;

-- 4.2 common 还有没有系统管理权限（期望：0 行）
select count(*) as common_system_perm_count
  from sys_role r
  join sys_role_menu rm on rm.role_id = r.role_id
  join sys_menu m on m.menu_id = rm.menu_id
 where r.role_key = 'common'
   and (m.perms like 'system:%' or m.perms like 'monitor:%' or m.perms like 'tool:%');
-- 期望：0

-- 4.3 两个角色的授权数与数据范围
select r.role_key, r.data_scope, count(rm.menu_id) as menu_count
  from sys_role r
  left join sys_role_menu rm on rm.role_id = r.role_id
 where r.role_key in ('admin', 'common')
 group by r.role_key, r.data_scope;
-- 期望：admin 全部菜单 + data_scope='1'；common 只剩业务菜单 + data_scope='5'
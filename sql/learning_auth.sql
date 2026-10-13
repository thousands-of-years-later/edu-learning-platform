-- ----------------------------
-- 手机号登录 / 注册  MVP 配套脚本
--
-- 说明：
-- 1. 账号即手机号。新注册用户的 login_name / phonenumber / user_name 都写入手机号，
--    user_type 为 '01'（注册用户），并自动关联 sys.account.defaultRole 配置的角色。
-- 2. 登录时先按 login_name 查询，查不到再按 phonenumber 查询（历史账号如 admin
--    无需改数据即可用手机号登录）。
-- 3. 本脚本可重复执行（幂等），对已存在的库不会破坏数据。
--
-- 执行方式（必须带字符集参数，否则中文注释会乱码）：
--   mysql --default-character-set=utf8mb4 -h43.142.9.214 -P3306 -uedu_dev_03 -p learning_platform < sql/learning_auth.sql
--
-- 注意：sys_config 在应用启动时被 SysConfigServiceImpl 全量加载进 EhCache，
--       执行完本脚本后需要【重启应用】，sys.account.registerUser 的改动才会生效。
-- ----------------------------

SET NAMES utf8mb4;

-- ----------------------------
-- 1、表结构（已存在则为空操作，仅用于新环境初始化时对照）
-- ----------------------------

-- 1.1 用户信息表
create table if not exists sys_user (
  user_id           bigint(20)      not null auto_increment    comment '用户ID',
  dept_id           bigint(20)      default null               comment '部门ID',
  login_name        varchar(30)     not null                   comment '登录账号',
  user_name         varchar(30)     default ''                 comment '用户昵称',
  user_type         varchar(2)      default '00'               comment '用户类型（00系统用户 01注册用户）',
  email             varchar(50)     default ''                 comment '用户邮箱',
  phonenumber       varchar(11)     default ''                 comment '手机号码',
  sex               char(1)         default '0'                comment '用户性别（0男 1女 2未知）',
  avatar            varchar(100)    default ''                 comment '头像路径',
  password          varchar(50)     default ''                 comment '密码',
  salt              varchar(20)     default ''                 comment '盐加密',
  status            char(1)         default '0'                comment '账号状态（0正常 1停用）',
  del_flag          char(1)         default '0'                comment '删除标志（0代表存在 2代表删除）',
  login_ip          varchar(128)    default ''                 comment '最后登录IP',
  login_date        datetime                                   comment '最后登录时间',
  pwd_update_date   datetime                                   comment '密码最后更新时间',
  create_by         varchar(64)     default ''                 comment '创建者',
  create_time       datetime                                   comment '创建时间',
  update_by         varchar(64)     default ''                 comment '更新者',
  update_time       datetime                                   comment '更新时间',
  remark            varchar(500)    default null               comment '备注',
  primary key (user_id)
) engine=innodb auto_increment=100 comment = '用户信息表';

-- 1.2 角色信息表
create table if not exists sys_role (
  role_id           bigint(20)      not null auto_increment    comment '角色ID',
  role_name         varchar(30)     not null                   comment '角色名称',
  role_key          varchar(100)    not null                   comment '角色权限字符串',
  role_sort         int(4)          not null                   comment '显示顺序',
  data_scope        char(1)         default '1'                comment '数据范围（1：全部数据权限 2：自定数据权限 3：本部门数据权限 4：本部门及以下数据权限）',
  status            char(1)         not null                   comment '角色状态（0正常 1停用）',
  del_flag          char(1)         default '0'                comment '删除标志（0代表存在 2代表删除）',
  create_by         varchar(64)     default ''                 comment '创建者',
  create_time       datetime                                   comment '创建时间',
  update_by         varchar(64)     default ''                 comment '更新者',
  update_time       datetime                                   comment '更新时间',
  remark            varchar(500)    default null               comment '备注',
  primary key (role_id)
) engine=innodb auto_increment=100 comment = '角色信息表';

-- 1.3 用户和角色关联表
create table if not exists sys_user_role (
  user_id   bigint(20) not null comment '用户ID',
  role_id   bigint(20) not null comment '角色ID',
  primary key(user_id, role_id)
) engine=innodb comment = '用户和角色关联表';

-- 1.4 参数配置表
create table if not exists sys_config (
  config_id         int(5)          not null auto_increment    comment '参数主键',
  config_name       varchar(100)    default ''                 comment '参数名称',
  config_key        varchar(100)    default ''                 comment '参数键名',
  config_value      varchar(500)    default ''                 comment '参数键值',
  config_type       char(1)         default 'N'                comment '系统内置（Y是 N否）',
  create_by         varchar(64)     default ''                 comment '创建者',
  create_time       datetime                                   comment '创建时间',
  update_by         varchar(64)     default ''                 comment '更新者',
  update_time       datetime                                   comment '更新时间',
  remark            varchar(500)    default null               comment '备注',
  primary key (config_id)
) engine=innodb auto_increment=100 comment = '参数配置表';

-- 关于索引：这里【不建】phonenumber / login_name 的唯一索引，原因有三：
--   1) 全库使用软删除（del_flag='2'），而 checkPhoneUnique / checkLoginNameUnique
--      只查 del_flag='0'。唯一索引会导致「注销过的手机号永久无法再注册」。
--   2) phonenumber 的默认值是 '' 而不是 NULL，唯一索引会让第二个未填手机号的
--      用户（原生后台允许创建）插入失败。
--   3) MySQL 没有部分索引，函数索引对空串同样无能为力。
-- 唯一性由应用层 checkPhoneUnique / checkLoginNameUnique 保证。

-- ----------------------------
-- 2、注册用户默认角色
--    sys.account.defaultRole 配置项的值即此处的 role_key，
--    原生已存在 role_key='common' 的「普通角色」，这里只做兜底创建。
-- ----------------------------
insert into sys_role (role_name, role_key, role_sort, data_scope, status, del_flag, create_by, create_time, remark)
select '普通角色', 'common', 2, '1', '0', '0', 'system', now(), '注册用户默认角色'
  from dual
 where not exists (select 1 from sys_role where role_key = 'common' and del_flag = '0');

-- ----------------------------
-- 3、新增配置项：注册用户默认角色
--    取不到该配置时应用会回退到 role_key='common'
-- ----------------------------
insert into sys_config (config_name, config_key, config_value, config_type, create_by, create_time, remark)
select '注册用户默认角色', 'sys.account.defaultRole', 'common', 'Y', 'system', now(), '注册接口分配的默认角色 role_key'
  from dual
 where not exists (select 1 from sys_config where config_key = 'sys.account.defaultRole');

-- ----------------------------
-- 4、开启注册功能
--    原生 /register 页面与 /api/register 接口共用该开关
-- ----------------------------
update sys_config
   set config_value = 'true',
       update_by    = 'system',
       update_time  = now()
 where config_key = 'sys.account.registerUser'
   and config_value <> 'true';

-- ----------------------------
-- 5、自检
-- ----------------------------
select config_key, config_value, remark from sys_config
 where config_key in ('sys.account.registerUser', 'sys.account.defaultRole');

select role_id, role_name, role_key, status, del_flag from sys_role order by role_id;

select user_id, login_name, user_name, user_type, phonenumber, status, del_flag from sys_user order by user_id;

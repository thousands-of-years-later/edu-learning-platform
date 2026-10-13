-- ----------------------------
-- 类别（Category）表结构与初始化数据
--
-- 说明：
-- 1. 课程和社区文章共用一张类别表 learning_category，用 category_type 区分归属模块：
--    course  = 课程模块的类别
--    article = 社区文章模块的类别
--    课程只能看到/维护 course 的数据，社区文章只能看到 article 的，互不干扰。
-- 2. 初始化 5 个类别：java、mysql、redis、kafka、计算机网络，每个模块各一份，共 10 条。
-- 3. 本脚本可重复执行（幂等），对已存在的库不会破坏数据。
--
-- 本脚本含类别表的写操作权限行（F 行，只授权限、不产生侧边栏菜单）。
-- 【不含】目录和菜单页：那部分在 sql/learning_category_menu.sql，
-- 等前端页面做好、控制器补回返回视图的方法之后再执行。
--
-- 执行方式（必须带字符集参数，否则中文注释会乱码）：
--   mysql --default-character-set=utf8mb4 -h43.142.9.214 -P3306 -uedu_dev_03 -p learning_platform < sql/learning_category.sql
--
-- 注意：字典（sys_dict_type / sys_dict_data）在应用启动时进缓存，
--       执行完本脚本后需要【重启应用】，类别类型字典才会生效。
-- ----------------------------

SET NAMES utf8mb4;

-- ----------------------------
-- 1、表结构（已存在则为空操作）
-- ----------------------------

create table if not exists learning_category (
  category_id     bigint(20)      not null auto_increment    comment '类别ID',
  category_name   varchar(50)     not null                   comment '类别名称',
  category_type   varchar(20)     not null                   comment '类别类型（course课程 article社区文章）',
  order_num       int(4)          default 0                  comment '显示顺序',
  status          char(1)         default '0'                comment '状态（0正常 1停用）',
  del_flag        char(1)         default '0'                comment '删除标志（0代表存在 2代表删除）',
  create_by       varchar(64)     default ''                 comment '创建者',
  create_time     datetime                                   comment '创建时间',
  update_by       varchar(64)     default ''                 comment '更新者',
  update_time     datetime                                   comment '更新时间',
  remark          varchar(500)    default null               comment '备注',
  primary key (category_id),
  key idx_category_type (category_type, del_flag)
) engine=innodb auto_increment=100 comment = '学习平台类别表';

-- 关于唯一索引：与 learning_auth.sql 中 sys_user 的处理同理，这里【不建】
-- (category_name, category_type) 唯一索引：
--   1) 全库使用软删除（del_flag='2'），唯一索引会导致「删除过的类别名永久无法再建」；
--   2) MySQL 没有部分索引，函数索引对软删除同样无能为力。
-- 唯一性由应用层 checkCategoryNameUnique 保证（只校验 del_flag='0'）。
-- 后续接入课程/文章表时，外键为 learning_category.category_id（建议逻辑外键，不加物理 FK）。

-- ----------------------------
-- 2、初始化类别：5 个名称 × 2 种类型 = 10 条
--    用 not exists 反连接保证重复执行不会产生重复数据
-- ----------------------------

insert into learning_category (category_name, category_type, order_num, status, del_flag, create_by, create_time, remark)
select n.category_name, t.category_type, n.order_num, '0', '0', 'admin', sysdate(), '类别初始化数据'
  from (
        select 'java' as category_name, 1 as order_num union all
        select 'mysql', 2 union all
        select 'redis', 3 union all
        select 'kafka', 4 union all
        select '计算机网络', 5
       ) n
  cross join (
        select 'course' as category_type union all
        select 'article'
       ) t
  left join learning_category c
         on c.category_name = n.category_name
        and c.category_type = t.category_type
        and c.del_flag = '0'
 where c.category_id is null;

-- ----------------------------
-- 3、新增字典：类别类型（前端列表页「类别类型」列用 $.table.selectDictLabel 渲染）
-- ----------------------------

insert into sys_dict_type (dict_name, dict_type, status, create_by, create_time, remark)
select '类别类型', 'sys_category_type', '0', 'admin', sysdate(), '学习平台类别类型列表'
  from dual
 where not exists (select 1 from sys_dict_type where dict_type = 'sys_category_type');

insert into sys_dict_data (dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status, create_by, create_time, remark)
select 1, '课程', 'course', 'sys_category_type', '', 'primary', 'Y', '0', 'admin', sysdate(), '课程模块类别'
  from dual
 where not exists (select 1 from sys_dict_data where dict_type = 'sys_category_type' and dict_value = 'course');

insert into sys_dict_data (dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status, create_by, create_time, remark)
select 2, '社区文章', 'article', 'sys_category_type', '', 'warning', 'N', '0', 'admin', sysdate(), '社区文章模块类别'
  from dual
 where not exists (select 1 from sys_dict_data where dict_type = 'sys_category_type' and dict_value = 'article');

-- ----------------------------
-- 4、类别写操作权限（只插 F 行，不会在侧边栏产生菜单）
--
-- 查询（list）接口按约定不校验权限、对所有登录用户开放，所以这里不插 :list，留着是误导。
-- F 行不会被 selectMenusByUserId / selectMenuNormalAll 取到（二者都过滤 menu_type in ('M','C')），
-- 但会被 selectPermsByUserId 取到 —— 因此只授予权限，不产生菜单项或死链接。
-- 等前端页面做好、控制器补回页面方法后，再执行 sql/learning_category_menu.sql 补完整的目录和菜单。
-- ----------------------------

insert into sys_menu (menu_name, parent_id, order_num, url, target, menu_type, visible, is_refresh, perms, icon, create_by, create_time, update_by, update_time, remark)
select concat(b.menu_label, a.action_label), 0, a.order_num, '#', 'menuItem', 'F', '0', '1',
       concat(b.perm_prefix, ':', a.action), '#', 'admin', sysdate(), '', null, '类别接口权限（无菜单）'
  from (
        select '课程类别' as menu_label, 'course:category'    as perm_prefix union all
        select '社区类别',              'community:category'
       ) b
  cross join (
        select '新增' as action_label, 'add'    as action, 1 as order_num union all
        select '修改',                'edit',           2 union all
        select '删除',                'remove',         3 union all
        select '导出',                'export',         4
       ) a
  left join sys_menu m on m.perms = concat(b.perm_prefix, ':', a.action)
 where m.menu_id is null;

-- 授权给「超级管理员」角色。role_key='admin' 的角色在 RuoYi 原始种子里一条授权都没有
-- （user_id==1 的 admin 由 *:*:* 绕过，不需要授权行），新建的管理员账号要靠这些行才有权限。
-- sys_role_menu 主键为 (role_id, menu_id)，insert ignore 保证可重复执行。
insert ignore into sys_role_menu (role_id, menu_id)
select r.role_id, m.menu_id
  from sys_role r
  cross join sys_menu m
 where r.role_key = 'admin'
   and (m.perms like 'course:category:%' or m.perms like 'community:category:%');

-- ----------------------------
-- 5、自检
-- ----------------------------
select category_id, category_name, category_type, order_num, status, del_flag
  from learning_category order by category_type, order_num;

select dict_type, dict_label, dict_value, is_default, status
  from sys_dict_data where dict_type = 'sys_category_type' order by dict_sort;

select menu_id, menu_name, parent_id, menu_type, perms
  from sys_menu where perms like '%:category:%' order by perms;

select r.role_key, count(1) as category_perm_count
  from sys_role_menu rm
  join sys_role r on r.role_id = rm.role_id
  join sys_menu m on m.menu_id = rm.menu_id
 where m.perms like '%:category:%'
 group by r.role_key;
-- 期望：类别 10 条（course 5 + article 5）、字典 2 条、
--       权限 8 条（course/community 各 add/edit/remove/export）、admin 角色授权 8 条

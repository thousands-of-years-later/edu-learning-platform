-- ----------------------------
-- 违禁词（帖子标题/正文拦截）
--
-- 规则：
--   1) 只拦截帖子（新增/编辑），评论不处理；
--   2) 后台可维护词库，命中后直接拒绝保存，统一提示"内容包含违禁词，请修改"；
--   3) 匹配前做归一化：去空格、去标点/符号、转小写，防"赌 博""赌*博"这类简单绕过；
--   4) 词库启动时加载到内存，后台增删改后自动刷新。
--
-- 本脚本可重复执行（幂等）。
-- 执行：
--   mysql --default-character-set=utf8mb4 -h43.142.9.214 -P3306 -uedu_dev_03 -p learning_platform < sql/learning_sensitive_word.sql
-- ----------------------------

SET NAMES utf8mb4;

-- 1、词库表
create table if not exists learning_sensitive_word
(
    word_id     bigint       not null auto_increment comment '主键',
    word        varchar(100) not null comment '违禁词',
    status      char(1)      not null default '0' comment '状态（0正常 1停用）',
    remark      varchar(255) null comment '备注',
    create_by   varchar(64)  null comment '创建者',
    create_time datetime     null comment '创建时间',
    update_by   varchar(64)  null comment '更新者',
    update_time datetime     null comment '更新时间',
    primary key (word_id),
    unique key uk_sensitive_word (word)
) engine = InnoDB default charset = utf8mb4 comment = '违禁词库';

-- 2、基础词（已存在则跳过）
insert ignore into learning_sensitive_word (word, status, remark, create_by, create_time)
values ('赌博', '0', '基础词', 'admin', sysdate()),
       ('诈骗', '0', '基础词', 'admin', sysdate()),
       ('代考', '0', '基础词', 'admin', sysdate()),
       ('刷单', '0', '基础词', 'admin', sysdate()),
       ('外挂', '0', '基础词', 'admin', sysdate());

-- 3、后台菜单（学习管理 → 违禁词管理）
set @learnMenuId := (select menu_id from sys_menu where menu_name = '学习管理' and parent_id = 0 limit 1);

insert into sys_menu (menu_name, parent_id, order_num, url, target, menu_type, visible, is_refresh, perms, icon, create_by, create_time, update_by, update_time, remark)
select '违禁词管理', @learnMenuId,
       (select ifnull(max(order_num), 0) + 1 from sys_menu where parent_id = @learnMenuId),
       '/community/sensitive-word', 'menuItem', 'C', '0', '1', 'community:sensitive:view', 'fa fa-ban',
       'admin', sysdate(), '', null, '帖子违禁词拦截词库'
  from dual
 where not exists (select 1 from sys_menu where perms = 'community:sensitive:view' and menu_type = 'C');

set @wordMenuId := (select menu_id from sys_menu where perms = 'community:sensitive:view' and menu_type = 'C' limit 1);

-- 列表按钮
insert into sys_menu (menu_name, parent_id, order_num, url, target, menu_type, visible, is_refresh, perms, icon, create_by, create_time, update_by, update_time, remark)
select '违禁词查询', @wordMenuId, 1, '#', 'menuItem', 'F', '0', '1', 'community:sensitive:list', '#', 'admin', sysdate(), '', null, ''
  from dual
 where not exists (select 1 from sys_menu where perms = 'community:sensitive:list' and menu_type = 'F');

-- 新增按钮
insert into sys_menu (menu_name, parent_id, order_num, url, target, menu_type, visible, is_refresh, perms, icon, create_by, create_time, update_by, update_time, remark)
select '违禁词新增', @wordMenuId, 2, '#', 'menuItem', 'F', '0', '1', 'community:sensitive:add', '#', 'admin', sysdate(), '', null, ''
  from dual
 where not exists (select 1 from sys_menu where perms = 'community:sensitive:add' and menu_type = 'F');

-- 修改按钮
insert into sys_menu (menu_name, parent_id, order_num, url, target, menu_type, visible, is_refresh, perms, icon, create_by, create_time, update_by, update_time, remark)
select '违禁词修改', @wordMenuId, 3, '#', 'menuItem', 'F', '0', '1', 'community:sensitive:edit', '#', 'admin', sysdate(), '', null, ''
  from dual
 where not exists (select 1 from sys_menu where perms = 'community:sensitive:edit' and menu_type = 'F');

-- 删除按钮
insert into sys_menu (menu_name, parent_id, order_num, url, target, menu_type, visible, is_refresh, perms, icon, create_by, create_time, update_by, update_time, remark)
select '违禁词删除', @wordMenuId, 4, '#', 'menuItem', 'F', '0', '1', 'community:sensitive:remove', '#', 'admin', sysdate(), '', null, ''
  from dual
 where not exists (select 1 from sys_menu where perms = 'community:sensitive:remove' and menu_type = 'F');

-- 4、授权给 admin
insert ignore into sys_role_menu (role_id, menu_id)
select r.role_id, m.menu_id
  from sys_role r
  join sys_menu m on m.perms like 'community:sensitive:%'
 where r.role_key = 'admin';

-- 自检
select word_id, word, status from learning_sensitive_word order by word_id;
select menu_id, menu_name, url, perms from sys_menu where perms like 'community:sensitive:%' order by menu_id;

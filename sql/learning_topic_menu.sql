-- ----------------------------
-- 话题管理 菜单与按钮权限
--
-- 页面与接口（learning-community 模块新增 TopicController）：
--   GET  /community/topic                话题列表页 → templates/community/topic/topic.html
--   GET  /community/topic/add、/edit/{topicId}
--   POST /community/topic/list、add、edit、remove、checkTopicNameUnique
--
-- 权限：
--   community:topic:view / list / add / edit / remove
--
-- 前置条件：先执行过 sql/learning_topic.sql（建表）
--           与 sql/learning_category_menu.sql（建「学习管理」目录）。
-- 本脚本可重复执行（幂等）。
--
-- 执行方式：
--   mysql --default-character-set=utf8mb4 -h43.142.9.214 -P3306 -uedu_dev_03 -p learning_platform < sql/learning_topic_menu.sql
--
-- 注意：菜单和角色授权在登录时进 Shiro 缓存，执行完需要【重启应用并重新登录】。
--
-- 授权口径：话题是管理员维护的内容（用户只能选），只授给 admin。
-- ----------------------------

SET NAMES utf8mb4;

-- ----------------------------
-- 1、菜单与按钮权限
-- ----------------------------
SET @learnMenuId := (SELECT menu_id FROM sys_menu WHERE menu_name = '学习管理' AND parent_id = 0 LIMIT 1);
SET @topicOrder := (SELECT IFNULL(MAX(order_num), 0) + 1 FROM sys_menu WHERE parent_id = @learnMenuId);

insert into sys_menu (menu_name, parent_id, order_num, url, target, menu_type, visible, is_refresh, perms, icon, create_by, create_time, update_by, update_time, remark)
select '话题管理', IFNULL(@learnMenuId, 0), @topicOrder, '/community/topic', 'menuItem', 'C', '0', '1', 'community:topic:view', 'fa fa-tags', 'admin', sysdate(), '', null, '话题管理菜单'
  from dual
 where not exists (select 1 from sys_menu where perms = 'community:topic:view' and menu_type = 'C');

SET @topicMenuId := (SELECT menu_id FROM sys_menu WHERE perms = 'community:topic:view' AND menu_type = 'C' LIMIT 1);

-- 按钮权限：列表、新增、修改、删除
insert into sys_menu (menu_name, parent_id, order_num, url, target, menu_type, visible, is_refresh, perms, icon, create_by, create_time, update_by, update_time, remark)
select concat('话题', a.action_label), IFNULL(@topicMenuId, 0), a.order_num, '#', 'menuItem', 'F', '0', '1',
       concat('community:topic:', a.action), '#', 'admin', sysdate(), '', null, ''
  from (
        select '列表' as action_label, 'list'   as action, 1 as order_num union all
        select '新增',                'add',           2 union all
        select '修改',                'edit',          3 union all
        select '删除',                'remove',        4
       ) a
  left join sys_menu m on m.perms = concat('community:topic:', a.action)
 where m.menu_id is null;

-- 把已存在的按钮权限挂回话题菜单
update sys_menu set parent_id = @topicMenuId
 where menu_type = 'F' and perms like 'community:topic:%' and parent_id <> @topicMenuId;

-- ----------------------------
-- 2、角色授权（只给 admin）
-- ----------------------------
insert ignore into sys_role_menu (role_id, menu_id)
select r.role_id, m.menu_id
  from sys_role r
  cross join sys_menu m
 where r.role_key = 'admin'
   and (m.perms in ('community:topic:view', 'community:topic:list', 'community:topic:add',
                    'community:topic:edit', 'community:topic:remove')
        or (m.menu_id = @learnMenuId and @learnMenuId is not null));

-- ----------------------------
-- 3、自检
-- ----------------------------
select menu_id, menu_name, parent_id, order_num, url, menu_type, perms
  from sys_menu
 where perms like 'community:topic:%' or (menu_name = '学习管理' and menu_type = 'M')
 order by parent_id, order_num, menu_id;

-- 期望：话题菜单 1 条 + 按钮 4 条；admin 授权 5 条，common 0 条

-- ----------------------------
-- 评论管理 菜单与按钮权限
--
-- 页面与接口（learning-interation 模块新增 CommentController）：
--   GET  /community/comment            评论列表页  → templates/community/comment/comment.html
--   POST /community/comment/list       列表数据
--   POST /community/comment/remove     删除（批量，逗号分隔）
--
-- 权限：
--   community:comment:view / list / remove
--
-- 前置条件：先执行过 sql/learning_comment.sql（建表）
--           与 sql/learning_category_menu.sql（建「学习管理」目录）。
-- 本脚本可重复执行（幂等）。
--
-- 执行方式：
--   mysql --default-character-set=utf8mb4 -h43.142.9.214 -P3306 -uedu_dev_03 -p learning_platform < sql/learning_comment_menu.sql
--
-- 注意：菜单和角色授权在登录时进 Shiro 缓存，执行完需要【重启应用并重新登录】。
--
-- 授权口径：评论管理是纯管理功能，只授给 admin（与课程管理一致），
--          普通用户不需要进后台删评论。
-- ----------------------------

SET NAMES utf8mb4;

-- ----------------------------
-- 1、菜单与按钮权限
-- ----------------------------

-- 1.1 复用「学习管理」目录（不依赖 LAST_INSERT_ID，按业务键回查）
SET @learnMenuId := (SELECT menu_id FROM sys_menu WHERE menu_name = '学习管理' AND parent_id = 0 LIMIT 1);

-- 1.2 评论管理菜单（order_num 取同级最大值 +1，不硬编码）
SET @commentOrder := (SELECT IFNULL(MAX(order_num), 0) + 1 FROM sys_menu WHERE parent_id = @learnMenuId);

insert into sys_menu (menu_name, parent_id, order_num, url, target, menu_type, visible, is_refresh, perms, icon, create_by, create_time, update_by, update_time, remark)
select '评论管理', IFNULL(@learnMenuId, 0), @commentOrder, '/community/comment', 'menuItem', 'C', '0', '1', 'community:comment:view', 'fa fa-comments', 'admin', sysdate(), '', null, '评论管理菜单'
  from dual
 where not exists (select 1 from sys_menu where perms = 'community:comment:view' and menu_type = 'C');

SET @commentMenuId := (SELECT menu_id FROM sys_menu WHERE perms = 'community:comment:view' AND menu_type = 'C' LIMIT 1);

-- 1.3 按钮权限：查看列表 + 删除
insert into sys_menu (menu_name, parent_id, order_num, url, target, menu_type, visible, is_refresh, perms, icon, create_by, create_time, update_by, update_time, remark)
select concat('评论', a.action_label), IFNULL(@commentMenuId, 0), a.order_num, '#', 'menuItem', 'F', '0', '1',
       concat('community:comment:', a.action), '#', 'admin', sysdate(), '', null, ''
  from (
        select '列表' as action_label, 'list'   as action, 1 as order_num union all
        select '删除',                'remove',        2
       ) a
  left join sys_menu m on m.perms = concat('community:comment:', a.action)
 where m.menu_id is null;

-- 1.4 把已存在的按钮权限挂回评论菜单（历史执行可能插成 parent_id=0）
update sys_menu set parent_id = @commentMenuId
 where menu_type = 'F' and perms like 'community:comment:%' and parent_id <> @commentMenuId;

-- ----------------------------
-- 2、角色授权（只给 admin）
-- ----------------------------
insert ignore into sys_role_menu (role_id, menu_id)
select r.role_id, m.menu_id
  from sys_role r
  cross join sys_menu m
 where r.role_key = 'admin'
   and (m.perms in ('community:comment:view', 'community:comment:list', 'community:comment:remove')
        or (m.menu_id = @learnMenuId and @learnMenuId is not null));

-- ----------------------------
-- 3、自检
-- ----------------------------
select menu_id, menu_name, parent_id, order_num, url, menu_type, perms
  from sys_menu
 where perms like 'community:comment:%' or (menu_name = '学习管理' and menu_type = 'M')
 order by parent_id, order_num, menu_id;

select r.role_key, count(1) as comment_menu_count
  from sys_role_menu rm
  join sys_role r on r.role_id = rm.role_id
  join sys_menu m on m.menu_id = rm.menu_id
 where m.perms like 'community:comment:%'
 group by r.role_key;
-- 期望：评论菜单 1 条 + 按钮 2 条；admin 授权 3 条，common 0 条

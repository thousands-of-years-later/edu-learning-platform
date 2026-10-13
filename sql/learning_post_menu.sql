-- ----------------------------
-- 社区帖子 菜单与按钮权限
--
-- 页面与接口（learning-community 模块）：
--   GET  /community/post              帖子列表页  → templates/community/post/post.html
--   GET  /community/post/add、/edit/{postId}、/detail/{postId}
--   POST /community/post/list、add、edit、remove、export
--
-- 前置条件：先执行过 sql/learning_post.sql（建表）
--           与 sql/learning_category_menu.sql（建「学习管理」目录）。
-- 本脚本可重复执行（幂等）。
--
-- 执行方式：
--   mysql --default-character-set=utf8mb4 -h43.142.9.214 -P3306 -uedu_dev_03 -p learning_platform < sql/learning_post_menu.sql
--
-- 注意：菜单和角色授权在登录时进 Shiro 缓存，执行完需要【重启应用并重新登录】。
--
-- 这里与 course 的授权是【刻意不同】的：
--   course 是「增删改只给 admin」，帖子必须让普通用户能发/改/删自己的帖子（需求），
--   所以 add/edit/remove 也授给 common，「只能动自己的」由服务层归属校验兜底
--   （PostServiceImpl.updatePost / deletePostByIds 逐个校验 user_id）。
-- ----------------------------

SET NAMES utf8mb4;

-- ----------------------------
-- 1、菜单与权限
--    菜单 url 指向控制器路径，menu_type：M 目录 / C 菜单 / F 按钮
-- ----------------------------

-- 1.1 复用 learning_category_menu.sql 建好的「学习管理」目录
--     不依赖 LAST_INSERT_ID()，一律按业务键回查，保证脚本可重复执行
SET @learnMenuId := (SELECT menu_id FROM sys_menu WHERE menu_name = '学习管理' AND parent_id = 0 LIMIT 1);

-- 1.2 社区帖子菜单（order_num=4，1、2、3 已被课程类别/社区类别/课程管理占用）
insert into sys_menu (menu_name, parent_id, order_num, url, target, menu_type, visible, is_refresh, perms, icon, create_by, create_time, update_by, update_time, remark)
select '社区帖子', IFNULL(@learnMenuId, 0), 4, '/community/post', 'menuItem', 'C', '0', '1', 'community:post:view', '#', 'admin', sysdate(), '', null, '社区模块帖子菜单'
  from dual
 where not exists (select 1 from sys_menu where perms = 'community:post:view' and menu_type = 'C');

SET @postMenuId := (SELECT menu_id FROM sys_menu WHERE perms = 'community:post:view' AND menu_type = 'C' LIMIT 1);

-- 1.3 按钮权限：社区帖子 4 个（add、edit、remove、export）
--     这里【不插 :list】—— 列表接口按约定不校验权限、对所有登录用户开放，
--     插一条不会被用到的权限行是误导（遵循 learning_category.sql 里更晚的约定，
--     course 脚本里插的 course:course:list 是更早的写法，此处不跟）。
insert into sys_menu (menu_name, parent_id, order_num, url, target, menu_type, visible, is_refresh, perms, icon, create_by, create_time, update_by, update_time, remark)
select concat('社区帖子', a.action_label), IFNULL(@postMenuId, 0), a.order_num, '#', 'menuItem', 'F', '0', '1',
       concat('community:post:', a.action), '#', 'admin', sysdate(), '', null, ''
  from (
        select '新增' as action_label, 'add'   as action, 1 as order_num union all
        select '修改',                'edit',          2 union all
        select '删除',                'remove',        3 union all
        select '导出',                'export',        4
       ) a
  left join sys_menu m on m.perms = concat('community:post:', a.action)
 where m.menu_id is null;

-- 1.4 把【已存在】的按钮权限挂到社区帖子菜单下
--     上面的插入带 `where m.menu_id is null`，对已存在的权限行会跳过、不会更新 parent_id。
--     若历史执行曾把 F 行插成 parent_id=0，这里重新挂回，避免「菜单管理」里显示成游离的顶级按钮权限。
--     @postMenuId 为 NULL 时 `parent_id <> NULL` 恒为 NULL，匹配不到任何行，安全。
update sys_menu set parent_id = @postMenuId
 where menu_type = 'F' and perms like 'community:post:%' and parent_id <> @postMenuId;

-- ----------------------------
-- 2、角色授权
--    sys_role_menu 主键为 (role_id, menu_id)，insert ignore 保证可重复执行。
--
--    与 course 的差别：帖子的新增/修改/删除也授给 common ——
--    需求是「普通用户对自己的帖子有增删改查权限」，只给 admin 就没人能发帖了。
--    越权由服务层兜底：非管理员只能操作 user_id 等于自己的帖子。
-- ----------------------------

insert ignore into sys_role_menu (role_id, menu_id)
select r.role_id, m.menu_id
  from sys_role r
  cross join sys_menu m
 where r.role_key in ('admin', 'common')
   and (m.perms in ('community:post:view', 'community:post:add', 'community:post:edit', 'community:post:remove')
        or (m.menu_id = @learnMenuId and @learnMenuId is not null));

insert ignore into sys_role_menu (role_id, menu_id)
select r.role_id, m.menu_id
  from sys_role r
  cross join sys_menu m
 where r.role_key = 'admin'
   and m.perms = 'community:post:export';

-- ----------------------------
-- 3、自检
-- ----------------------------
select menu_id, menu_name, parent_id, order_num, url, menu_type, perms
  from sys_menu
 where perms like 'community:post:%' or (menu_name = '学习管理' and menu_type = 'M')
 order by parent_id, order_num, menu_id;

select r.role_key, count(1) as post_menu_count
  from sys_role_menu rm
  join sys_role r on r.role_id = rm.role_id
  join sys_menu m on m.menu_id = rm.menu_id
 where m.perms like 'community:post:%'
 group by r.role_key;
-- 期望：社区帖子菜单 1 条 + 按钮权限 4 条；
--       授权 admin=5（菜单 + 4 个按钮）、common=4（菜单 + add/edit/remove）

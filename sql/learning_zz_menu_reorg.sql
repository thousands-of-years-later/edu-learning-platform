-- ----------------------------
-- 后台菜单调整：取消「评论管理」「评价管理」两个独立菜单
--
-- 背景（页面分布优化）：
--   评论不再单独做一个管理页，改成在「课程管理 / 社区帖子 / 短视频管理」每一行点「评论」，
--   直接查看该内容的评论（内容信息 + 一级评论 + 回复）；因此独立的评论管理菜单取消。
--   评价管理页也一并取消（评分数据仍保留在 learning_course_rating 表，课程端接口照常返回）。
--
-- 本脚本只做删除，可重复执行（幂等）。
-- 执行顺序：必须在 learning_comment_menu.sql、learning_user_admin_menu.sql 之后执行
--           （这两个脚本会创建对应菜单，本脚本负责把它们删掉）。
--
-- 执行方式：
--   mysql --default-character-set=utf8mb4 -h43.142.9.214 -P3306 -uedu_dev_03 -p learning_platform < sql/learning_zz_menu_reorg.sql
--
-- 注意：菜单与角色授权在登录时进 Shiro 缓存，执行完需要【重启应用并重新登录】。
-- ----------------------------

SET NAMES utf8mb4;

-- 1、先删角色授权（外键式，避免残留 sys_role_menu 指向已删菜单）
delete rm
  from sys_role_menu rm
  join sys_menu m on m.menu_id = rm.menu_id
 where m.perms like 'community:comment:%'
    or m.perms like 'course:rating:%';

-- 2、再删菜单与按钮权限
delete from sys_menu
 where perms like 'community:comment:%'
    or perms like 'course:rating:%';

-- 3、自检：期望两条查询结果都为 0
select count(*) as 残留角色授权数
  from sys_role_menu rm
  join sys_menu m on m.menu_id = rm.menu_id
 where m.perms like 'community:comment:%'
    or m.perms like 'course:rating:%';

select count(*) as 残留菜单数
  from sys_menu
 where perms like 'community:comment:%'
    or perms like 'course:rating:%';

-- 4、菜单序号补平（删掉两个菜单后 8、10 号空缺，按当前顺序重排成 1..N，避免看起来缺项）
set @learnMenuId := (select menu_id from sys_menu where menu_name = '学习管理' and parent_id = 0 limit 1);
update sys_menu m
  join (
        select menu_id, row_number() over (order by order_num, menu_id) as rn
          from sys_menu
         where parent_id = @learnMenuId and menu_type = 'C'
       ) t on t.menu_id = m.menu_id
   set m.order_num = t.rn;

-- 5、顺带确认「学习管理」下还剩下哪些菜单
select menu_id, menu_name, order_num, url, menu_type, perms
  from sys_menu
 where parent_id = @learnMenuId
 order by order_num, menu_id;

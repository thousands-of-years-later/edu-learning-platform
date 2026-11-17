-- ----------------------------
-- 平台协议（用户协议 / 隐私政策 / 关于我们）建表脚本
--
-- 对应代码：learning-user 模块 com.ruoyi.user.domain.Agreement
-- 页面：templates/user/agreement/  →  /user/agreement
--
-- 设计说明：
--   1) 三种协议固定用 agreement_type 区分：
--        user_agreement  用户协议
--        privacy_policy  隐私政策
--        about_us        关于我们
--      业务上每种类型只保留一条未删除记录，新增/修改时由服务层查重。
--   2) 【不建】agreement_type 唯一索引：本表统一软删除（del_flag='2'），
--      (agreement_type, del_flag) 唯一索引会挡住「删掉后重新新增同类型」；
--      MySQL 也不支持只对部分行生效的唯一索引。后台由管理员单人维护，
--      服务层查重足够，接口里会给出「该类型的协议已存在」的明确提示。
--   3) content 用 longtext 存 HTML（后台 summernote 编辑）。
--      列表接口不查该字段，只有编辑页按 ID 查询时带出正文。
--   4) 外键一律为逻辑外键，不加物理 FK（与 learning_course.sql 的约定相同）。
--
-- 本脚本可重复执行（幂等）：
--   - 建表用 create table if not exists；
--   - 三条种子只在「该类型一条记录都没有（含已删除）」时插入，
--     重复执行不会覆盖后台改过的内容，也不会把管理员删掉的协议重新种回来。
--
-- 执行方式（必须带字符集参数，否则中文注释会乱码）：
--   mysql --default-character-set=utf8mb4 -h<DB_HOST> -P3306 -u<DB_USER> -p <DB_NAME> < sql/learning_agreement.sql
--
-- 执行顺序：本脚本之后执行 sql/learning_agreement_menu.sql（菜单与角色授权）。
-- ----------------------------

SET NAMES utf8mb4;

-- ----------------------------
-- 1、表结构（已存在则为空操作）
-- ----------------------------
create table if not exists learning_agreement (
  agreement_id   bigint       not null auto_increment          comment '协议ID',
  agreement_type varchar(32)  not null                         comment '协议类型（user_agreement=用户协议 privacy_policy=隐私政策 about_us=关于我们）',
  title          varchar(100) not null                         comment '协议标题',
  content        longtext                                      comment '协议正文（HTML）',
  order_num      int          default 0                        comment '显示顺序',
  status         char(1)      default '0'                      comment '状态（0正常 1停用）',
  del_flag       char(1)      default '0'                      comment '删除标志（0存在 2删除）',
  create_by      varchar(64)  default ''                       comment '创建者',
  create_time    datetime     default null                     comment '创建时间',
  update_by      varchar(64)  default ''                       comment '更新者',
  update_time    datetime     default null                     comment '更新时间',
  remark         varchar(500) default null                     comment '备注',
  primary key (agreement_id),
  key idx_agreement_type (agreement_type, del_flag, status, order_num)
) engine = innodb default charset = utf8mb4 comment = '学习平台-平台协议（用户协议/隐私政策/关于我们）';

-- ----------------------------
-- 2、种子数据：三种协议各一条占位记录
--    管理员在后台「协议管理」里完善正文即可，不需要再新增。
--    where not exists 判断时【故意不过滤 del_flag】：管理员删掉的协议不会被脚本重新种回来。
-- ----------------------------
insert into learning_agreement (agreement_type, title, content, order_num, status, del_flag, create_by, create_time, remark)
select 'user_agreement', '用户协议',
       '<h2>用户协议</h2><p>欢迎使用本学习平台。本协议约定您与平台之间的权利与义务，请仔细阅读。</p><p>（内容由管理员在后台「协议管理」中完善）</p>',
       1, '0', '0', 'admin', sysdate(), '系统初始化'
  from dual
 where not exists (select 1 from learning_agreement where agreement_type = 'user_agreement');

insert into learning_agreement (agreement_type, title, content, order_num, status, del_flag, create_by, create_time, remark)
select 'privacy_policy', '隐私政策',
       '<h2>隐私政策</h2><p>我们重视您的个人信息保护。本政策说明我们如何收集、使用和保护您的信息。</p><p>（内容由管理员在后台「协议管理」中完善）</p>',
       2, '0', '0', 'admin', sysdate(), '系统初始化'
  from dual
 where not exists (select 1 from learning_agreement where agreement_type = 'privacy_policy');

insert into learning_agreement (agreement_type, title, content, order_num, status, del_flag, create_by, create_time, remark)
select 'about_us', '关于我们',
       '<h2>关于我们</h2><p>本学习平台致力于提供课程、社区与短视频学习体验。</p><p>（内容由管理员在后台「协议管理」中完善）</p>',
       3, '0', '0', 'admin', sysdate(), '系统初始化'
  from dual
 where not exists (select 1 from learning_agreement where agreement_type = 'about_us');

-- ----------------------------
-- 3、自检
-- ----------------------------
select table_name, table_comment
  from information_schema.tables
 where table_schema = database()
   and table_name = 'learning_agreement';

select index_name, seq_in_index, column_name
  from information_schema.statistics
 where table_schema = database()
   and table_name = 'learning_agreement'
 order by index_name, seq_in_index;
-- 期望：idx_agreement_type 4 列

select agreement_id, agreement_type, title, order_num, status, del_flag, create_time
  from learning_agreement
 order by order_num, agreement_id;
-- 期望：三条记录（user_agreement / privacy_policy / about_us），del_flag 均为 0

-- 完整性检查：同类型未删除记录出现多条（期望 0 行）
select agreement_type, count(1) as cnt
  from learning_agreement
 where del_flag = '0'
 group by agreement_type
having count(1) > 1;

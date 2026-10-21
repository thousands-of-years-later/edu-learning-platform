-- ----------------------------
-- 课程轮播图（Banner）表结构
--
-- 说明：
-- 1. 每个课程类别各有一个轮播窗口，另有一个独立的「全部」窗口。
--    「全部」用 category_id = 0 表示，【不是】各类别的汇总：
--    管理员在后台单独给「全部」配轮播，与其他类别互不影响。
--    不用 NULL 表示「全部」—— 查询统一 `where category_id = ?`，无分支，索引也能用上。
-- 2. 一条轮播只属于一个类别（一对一，直接存 category_id，不加关联表）。
-- 3. 点击轮播只跳课程：course_id 逻辑关联 learning_course.course_id。
-- 4. 外键一律为逻辑外键，不加物理 FK（与 learning_course.sql §3 的约定相同）。
-- 5. 本脚本可重复执行（幂等），对已存在的库不会破坏数据。
--
-- 【不含种子数据】：类别能播种（learning_category.sql）是因为它是自包含的引用数据；
-- 轮播是内容，其 course_id 依赖各环境实际课程 ID（auto_increment 起始不同），
-- 种下去只会在后台留下一批指向不存在课程的脏数据。第一条轮播由管理员在后台手工创建。
--
-- 【App 端的静默隐藏】：课程被停用（status='1'）或软删（del_flag='2'）后，
-- 指向它的轮播会从 App 接口里消失，但后台列表仍显示为「正常」。
-- 这是刻意设计：课程停用是可逆的，读时过滤比级联删轮播安全。
--
-- 执行方式（必须带字符集参数，否则中文注释会乱码）：
--   mysql --default-character-set=utf8mb4 -h43.142.9.214 -P3306 -uedu_dev_03 -p learning_platform < sql/learning_banner.sql
--
-- 菜单与按钮权限在 sql/learning_banner_menu.sql。
-- ----------------------------

SET NAMES utf8mb4;

-- ----------------------------
-- 1、表结构（已存在则为空操作）
-- ----------------------------

create table if not exists learning_banner (
  banner_id   bigint(20)   not null auto_increment comment '轮播图ID',
  category_id bigint(20)   not null default 0       comment '所属类别ID（0=全部；其余逻辑关联 learning_category.category_id）',
  title       varchar(100) not null                 comment '轮播标题',
  image_url   varchar(500) not null default ''      comment '轮播图片地址（上传返回的相对路径 /profile/...，OSS 开启时为完整 URL）',
  course_id   bigint(20)   not null                 comment '点击跳转的课程ID（逻辑关联 learning_course.course_id）',
  order_num   int(4)       default 0                comment '显示顺序',
  status      char(1)      default '0'              comment '状态（0正常 1停用）',
  del_flag    char(1)      default '0'              comment '删除标志（0代表存在 2代表删除）',
  create_by   varchar(64)  default ''               comment '创建者',
  create_time datetime                              comment '创建时间',
  update_by   varchar(64)  default ''               comment '更新者',
  update_time datetime                              comment '更新时间',
  remark      varchar(500) default null             comment '备注',
  primary key (banner_id),
  key idx_banner_category (category_id, del_flag, status, order_num)
) engine=innodb auto_increment=100 comment = '学习平台课程轮播图表';

-- 关于唯一索引：与 learning_category / learning_course 同理，这里【不建】title 唯一索引 ——
--   1) 全库使用软删除（del_flag='2'），唯一索引会导致「删除过的标题永久无法再用」；
--   2) 轮播标题本来也不要求唯一（同一个活动可以在多个类别窗口出现，各建一条即可）。
--
-- 关于索引列序 idx_banner_category (category_id, del_flag, status, order_num)：
--   等值条件列在前（category_id 必带，del_flag/status 在 App 查询里都是等值），
--   排序列 order_num 放最后，让 order by 直接吃到索引；
--   索引末尾 InnoDB 会自动补主键 banner_id，正好兜住 `order by order_num, banner_id` 的 tiebreaker。

-- ----------------------------
-- 2、自检
-- ----------------------------

select table_name, table_comment
  from information_schema.tables
 where table_schema = database()
   and table_name = 'learning_banner';

select count(1) as column_count
  from information_schema.columns
 where table_schema = database() and table_name = 'learning_banner';

select index_name, seq_in_index, column_name
  from information_schema.statistics
 where table_schema = database() and table_name = 'learning_banner'
 order by index_name, seq_in_index;
-- 期望：1 张表、13 列、1 个索引（idx_banner_category 4 列）

select count(1) as banner_count from learning_banner;
-- 期望：0（不播种数据，由管理员在后台创建）

-- 完整性检查 1：轮播指向的课程不存在 / 已被软删（期望 0 行）
select b.banner_id, b.title, b.course_id
  from learning_banner b
  left join learning_course c on c.course_id = b.course_id
 where b.del_flag = '0' and (c.course_id is null or c.del_flag = '2');

-- 完整性检查 2：category_id 非 0 却指向不存在 / 非 course 类型 / 已软删的类别（期望 0 行）
select b.banner_id, b.title, b.category_id
  from learning_banner b
  left join learning_category cat on cat.category_id = b.category_id
 where b.del_flag = '0' and b.category_id <> 0
   and (cat.category_id is null or cat.category_type <> 'course' or cat.del_flag = '2');

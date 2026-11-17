-- ----------------------------
-- 积分商城 建表脚本
--
-- 对应模块：shopping（com.ruoyi.shopping.*）
-- 四张表：
--   learning_shop_category  商品分类（后台维护）
--   learning_shop_product   商品（后台维护，用户端只读 + 兑换）
--   learning_shop_order     兑换记录（兑换即生成唯一兑换码；不可修改、不存配送信息）
--   learning_shop_address   收货地址（用户端独立 CRUD，与兑换无关）
--
-- 关键约束：
--   1) uk_shop_order_code：兑换码唯一，是并发下防重复码的最终闸门；
--   2) uk_shop_address_default (user_id, is_default)：is_default 只有 '1' 或 NULL，
--      MySQL 允许多个 NULL，从而保证每个用户最多一个默认地址；
--      逻辑删除地址时必须同时把 is_default 置 NULL，否则会挡住后续新增默认地址。
--
-- 本脚本可重复执行（幂等）：只用 create table if not exists，不插入/不删除业务数据。
--
-- 执行方式（建议先在本地克隆库执行验证；共享库执行前先备份）：
--   mysql --default-character-set=utf8mb4 -h<DB_HOST> -P3306 -u<DB_USER> -p <DB_NAME> < sql/learning_shop.sql
--
-- 执行顺序：本脚本之后执行 sql/learning_shop_menu.sql（菜单与角色授权）。
-- ----------------------------

SET NAMES utf8mb4;

-- ----------------------------
-- 1、商品分类
-- ----------------------------
create table if not exists learning_shop_category (
  category_id   bigint       not null auto_increment          comment '分类ID',
  category_name varchar(50)  not null                         comment '分类名称',
  order_num     int          default 0                        comment '显示顺序',
  status        char(1)      default '0'                      comment '状态（0正常 1停用）',
  del_flag      char(1)      default '0'                      comment '删除标志（0存在 2删除）',
  create_by     varchar(64)  default ''                       comment '创建者',
  create_time   datetime     default null                     comment '创建时间',
  update_by     varchar(64)  default ''                       comment '更新者',
  update_time   datetime     default null                     comment '更新时间',
  remark        varchar(500) default null                     comment '备注',
  primary key (category_id),
  key idx_shop_category_status (status, del_flag, order_num)
) engine = innodb default charset = utf8mb4 comment = '积分商城-商品分类';

-- ----------------------------
-- 2、商品
-- ----------------------------
create table if not exists learning_shop_product (
  product_id   bigint        not null auto_increment           comment '商品ID',
  category_id  bigint        not null                          comment '分类ID（learning_shop_category.category_id）',
  product_name varchar(100)  not null                          comment '商品名称',
  cover_url    varchar(500)  default null                      comment '商品封面（相对路径或完整 OSS 地址）',
  points_price int           not null default 0                comment '兑换所需积分',
  stock        int           not null default 0                comment '库存',
  sold_count   int           not null default 0                comment '已兑换数量',
  description  varchar(2000) default null                      comment '商品描述',
  order_num    int           default 0                         comment '显示顺序',
  status       char(1)       default '0'                       comment '状态（0上架 1下架）',
  del_flag     char(1)       default '0'                       comment '删除标志（0存在 2删除）',
  create_by    varchar(64)   default ''                        comment '创建者',
  create_time  datetime      default null                      comment '创建时间',
  update_by    varchar(64)   default ''                        comment '更新者',
  update_time  datetime      default null                      comment '更新时间',
  remark       varchar(500)  default null                      comment '备注',
  primary key (product_id),
  key idx_shop_product_category (category_id),
  key idx_shop_product_status (status, del_flag, order_num)
) engine = innodb default charset = utf8mb4 comment = '积分商城-商品';

-- ----------------------------
-- 3、兑换记录
--    不存收货地址、不存配送/核销状态：兑换只做「扣积分 + 扣库存 + 生成码」。
--    product_name / cover_url 是下单快照，商品后续改名/删除不影响历史记录。
-- ----------------------------
create table if not exists learning_shop_order (
  order_id        bigint       not null auto_increment         comment '兑换记录ID',
  redemption_code varchar(32)  not null                        comment '兑换码（全局唯一）',
  user_id         bigint       not null                        comment '用户ID（sys_user.user_id）',
  product_id      bigint       not null                        comment '商品ID',
  product_name    varchar(100) not null                        comment '商品名称快照',
  cover_url       varchar(500) default null                    comment '商品封面快照',
  points_cost     int          not null                        comment '消耗积分快照',
  create_time     datetime     default null                    comment '兑换时间',
  primary key (order_id),
  unique key uk_shop_order_code (redemption_code),
  key idx_shop_order_user (user_id, order_id),
  key idx_shop_order_product (product_id)
) engine = innodb default charset = utf8mb4 comment = '积分商城-兑换记录';

-- ----------------------------
-- 4、收货地址
--    与兑换无关，仅做用户地址簿；不实现配送。
--    is_default：'1' 默认 / NULL 非默认（唯一键配合 NULL 实现「最多一个默认」）。
-- ----------------------------
create table if not exists learning_shop_address (
  address_id     bigint       not null auto_increment         comment '地址ID',
  user_id        bigint       not null                        comment '用户ID（sys_user.user_id）',
  receiver_name  varchar(30)  not null                        comment '收货人',
  receiver_phone varchar(20)  not null                        comment '收货人手机号',
  detail_address varchar(255) not null                        comment '详细地址',
  is_default     char(1)      default null                    comment '默认地址（1默认；NULL非默认）',
  del_flag       char(1)      default '0'                     comment '删除标志（0存在 2删除）',
  create_time    datetime     default null                    comment '创建时间',
  update_time    datetime     default null                    comment '更新时间',
  primary key (address_id),
  unique key uk_shop_address_default (user_id, is_default),
  key idx_shop_address_user (user_id, del_flag)
) engine = innodb default charset = utf8mb4 comment = '积分商城-收货地址';

-- ----------------------------
-- 自检
-- ----------------------------
select table_name, table_comment
  from information_schema.tables
 where table_schema = database()
   and table_name in ('learning_shop_category', 'learning_shop_product',
                      'learning_shop_order', 'learning_shop_address')
 order by table_name;
-- 期望：4 张表

select table_name, index_name, column_name
  from information_schema.statistics
 where table_schema = database()
   and table_name in ('learning_shop_category', 'learning_shop_product',
                      'learning_shop_order', 'learning_shop_address')
   and non_unique = 0
 order by table_name, index_name, seq_in_index;
-- 期望包含：uk_shop_order_code(redemption_code)、uk_shop_address_default(user_id, is_default)

select (select count(*) from learning_shop_category) as category_rows,
       (select count(*) from learning_shop_product)  as product_rows,
       (select count(*) from learning_shop_order)    as order_rows,
       (select count(*) from learning_shop_address)  as address_rows;
-- 期望：初始化后都是 0

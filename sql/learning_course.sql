-- ----------------------------
-- 课程（Course）/ 章节（Chapter）/ 视频（Video）表结构
--
-- 说明：
-- 1. 三张表构成课程内容主体：
--    learning_course         课程（封面、简介、点赞/收藏/评论计数）
--    learning_course_chapter 可选章节，章节就是一段文字
--    learning_course_video   视频，chapter_id 为空表示不挂章节、直接属于课程
-- 2. 全部沿用软删除（del_flag：0代表存在 2代表删除），与 learning_category 保持一致。
-- 3. 外键一律为逻辑外键，不加物理 FK（与 learning_category.sql 的约定相同）：
--    learning_course.category_id       -> learning_category.category_id
--    learning_course_chapter.course_id -> learning_course.course_id
--    learning_course_video.course_id   -> learning_course.course_id
--    learning_course_video.chapter_id  -> learning_course_chapter.chapter_id（可空）
-- 4. 本脚本可重复执行（幂等），对已存在的库不会破坏数据。
--
-- 执行方式（必须带字符集参数，否则中文注释会乱码）：
--   mysql --default-character-set=utf8mb4 -h43.142.9.214 -P3306 -uedu_dev_03 -p learning_platform < sql/learning_course.sql
--
-- 菜单与按钮权限在 sql/learning_course_menu.sql。
-- ----------------------------

SET NAMES utf8mb4;

-- ----------------------------
-- 1、课程表
-- ----------------------------

create table if not exists learning_course (
  course_id       bigint(20)      not null auto_increment    comment '课程ID',
  course_name     varchar(100)    not null                   comment '课程名称',
  category_id     bigint(20)      default null               comment '所属类别ID（逻辑关联 learning_category.category_id）',
  cover_url       varchar(500)    default ''                 comment '封面图片地址（上传返回的相对路径 /profile/...）',
  intro           varchar(1000)   default ''                 comment '课程简介',
  like_count      int(11)         default 0                  comment '点赞数',
  favorite_count  int(11)         default 0                  comment '收藏数',
  comment_count   int(11)         default 0                  comment '评论数',
  status          char(1)         default '0'                comment '状态（0正常 1停用）',
  del_flag        char(1)         default '0'                comment '删除标志（0代表存在 2代表删除）',
  create_by       varchar(64)     default ''                 comment '创建者',
  create_time     datetime                                   comment '创建时间',
  update_by       varchar(64)     default ''                 comment '更新者',
  update_time     datetime                                   comment '更新时间',
  remark          varchar(500)    default null               comment '备注',
  primary key (course_id),
  key idx_course_category (category_id, del_flag)
) engine=innodb auto_increment=100 comment = '学习平台课程表';

-- 关于唯一索引：与 learning_category 同理，这里【不建】course_name 唯一索引 ——
-- 软删除下唯一索引会让「删除过的课程名永久无法再建」，唯一性由应用层
-- checkCourseNameUnique 保证（只校验 del_flag='0'）。

-- ----------------------------
-- 2、章节表（可选，章节就是一段文字）
-- ----------------------------

create table if not exists learning_course_chapter (
  chapter_id      bigint(20)      not null auto_increment    comment '章节ID',
  course_id       bigint(20)      not null                   comment '所属课程ID（逻辑关联 learning_course.course_id）',
  chapter_title   varchar(100)    not null                   comment '章节标题',
  chapter_content varchar(2000)   default ''                 comment '章节内容（一段文字）',
  order_num       int(4)          default 0                  comment '显示顺序',
  del_flag        char(1)         default '0'                comment '删除标志（0代表存在 2代表删除）',
  create_by       varchar(64)     default ''                 comment '创建者',
  create_time     datetime                                   comment '创建时间',
  update_by       varchar(64)     default ''                 comment '更新者',
  update_time     datetime                                   comment '更新时间',
  primary key (chapter_id),
  key idx_chapter_course (course_id, del_flag)
) engine=innodb auto_increment=100 comment = '学习平台课程章节表';

-- ----------------------------
-- 3、视频表
--    chapter_id 可空：为空表示该视频不挂章节，直接属于课程。
--    只有一张视频表，不区分「有章节/无章节」两套关系。
-- ----------------------------

create table if not exists learning_course_video (
  video_id        bigint(20)      not null auto_increment    comment '视频ID',
  course_id       bigint(20)      not null                   comment '所属课程ID（逻辑关联 learning_course.course_id）',
  chapter_id      bigint(20)      default null               comment '所属章节ID（逻辑关联 learning_course_chapter.chapter_id，为空表示无章节）',
  video_title     varchar(100)    not null                   comment '视频标题',
  video_url       varchar(500)    default ''                 comment '视频地址（上传返回的相对路径 /profile/...）',
  order_num       int(4)          default 0                  comment '显示顺序',
  del_flag        char(1)         default '0'                comment '删除标志（0代表存在 2代表删除）',
  create_by       varchar(64)     default ''                 comment '创建者',
  create_time     datetime                                   comment '创建时间',
  update_by       varchar(64)     default ''                 comment '更新者',
  update_time     datetime                                   comment '更新时间',
  primary key (video_id),
  key idx_video_course (course_id, del_flag),
  key idx_video_chapter (chapter_id, del_flag)
) engine=innodb auto_increment=100 comment = '学习平台课程视频表';

-- ----------------------------
-- 4、自检
-- ----------------------------
select table_name, table_comment
  from information_schema.tables
 where table_schema = database()
   and table_name in ('learning_course', 'learning_course_chapter', 'learning_course_video')
 order by table_name;
-- 期望：3 张表

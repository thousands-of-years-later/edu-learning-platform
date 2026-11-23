-- ----------------------------
-- 智能体会话 / 消息 / 异步任务
-- 表结构：用户维度，Java 负责落库；Python 服务无状态
-- ----------------------------
SET NAMES utf8mb4;

create table if not exists learning_agent_session (
  session_id        bigint       not null auto_increment comment '会话ID',
  user_id           bigint       not null comment '用户ID',
  title             varchar(100) not null default '新会话' comment '会话标题',
  scene             varchar(30)  not null default 'chat' comment '场景：chat/sql/video/ppt',
  status            char(1)      not null default '0' comment '0正常 1删除',
  last_message_time datetime     null comment '最后消息时间',
  create_time       datetime     null,
  update_time       datetime     null,
  primary key (session_id),
  key idx_agent_session_user (user_id, status, last_message_time)
) engine=InnoDB default charset=utf8mb4 comment='智能体会话';

create table if not exists learning_agent_message (
  message_id   bigint      not null auto_increment comment '消息ID',
  session_id   bigint      not null comment '会话ID',
  user_id      bigint      not null comment '用户ID',
  role         varchar(20) not null comment 'user/assistant/system/tool',
  content      mediumtext  null comment '消息内容',
  content_type varchar(20) not null default 'text' comment 'text/json',
  refs_json    text        null comment '引用来源JSON',
  tokens       int         not null default 0 comment 'token数',
  status       char(1)     not null default '0' comment '0正常 1删除',
  create_time  datetime    null,
  primary key (message_id),
  key idx_agent_message_session (session_id, message_id)
) engine=InnoDB default charset=utf8mb4 comment='智能体消息';

create table if not exists learning_agent_task (
  task_id         bigint       not null auto_increment comment '任务ID',
  user_id         bigint       not null comment '用户ID',
  session_id      bigint       null comment '关联会话ID',
  task_type       varchar(30)  not null comment 'video_summary/ppt',
  status          varchar(20)  not null default 'PENDING' comment 'PENDING/RUNNING/SUCCESS/FAILED/CANCELED',
  progress        int          not null default 0 comment '0~100',
  input_json      text         null comment '输入参数',
  output_json     mediumtext   null comment '输出结果',
  error_msg       varchar(500) null comment '失败原因',
  retry_count     int          not null default 0 comment '已重试次数',
  idempotency_key varchar(64)  null comment '幂等键',
  create_time     datetime     null,
  start_time      datetime     null,
  finish_time     datetime     null,
  update_time     datetime     null,
  primary key (task_id),
  unique key uk_agent_task_idem (idempotency_key),
  key idx_agent_task_user (user_id, task_type, create_time)
) engine=InnoDB default charset=utf8mb4 comment='智能体异步任务';

-- ----------------------------
-- 演示积分数据重建：让签到积分严格按 1~7 分规则，且今天留空可测试签到
--
-- 背景：早期签到档位是 5/8/10/12/15/18/20，演示库里还留着按旧档位发的记录，
--       与新规则（7 天循环 1~7 分）不一致。本脚本把演示账号的积分数据整体重建：
--         · 签到记录与签到流水按连续天数 1..N 给分（1、2、3…）；
--         · 观看课程/帖子/广告补几条示例流水；
--         · 余额、累计获得、每条流水的 balance 全部按时间顺序重算；
--         · 今天不签到，测试同学可以自己签，看到"下一天"的分数。
--
-- 演示账号：
--   111 连续签到 7 天（1~7 分，今天已签，完整展示 7 天循环）
--   112 连续签到 3 天（1~3 分），今天留空，可签出第 4 天 4 分
--   115 连续签到 2 天（1~2 分），今天留空
--   118 连续签到 2 天（1~2 分），今天留空
--
-- 幂等：先按用户清空积分流水/签到/去重表再重建，可重复执行。
-- 执行：
--   mysql --default-character-set=utf8mb4 -h43.142.9.214 -P3306 -uedu_dev_03 -p learning_platform < sql/learning_demo_reseed_points.sql
-- ----------------------------

SET NAMES utf8mb4;

-- 1、清空演示账号的积分相关数据
delete from learning_points_log   where user_id in (111, 112, 115, 118);
delete from learning_checkin      where user_id in (111, 112, 115, 118);
delete from learning_points_watch where user_id in (111, 112, 115, 118);
delete from learning_ad_view      where user_id in (111, 112, 115, 118);

-- 2、签到记录：连续 1..N 天，积分 = 连续天数（1、2、3…），今天留空
insert into learning_checkin (user_id, sign_date, points, continuous_days, create_time) values
(111, date_sub(curdate(), interval 6 day), 1, 1, timestamp(date_sub(curdate(), interval 6 day), '10:00:00')),
(111, date_sub(curdate(), interval 5 day), 2, 2, timestamp(date_sub(curdate(), interval 5 day), '10:00:00')),
(111, date_sub(curdate(), interval 4 day), 3, 3, timestamp(date_sub(curdate(), interval 4 day), '10:00:00')),
(111, date_sub(curdate(), interval 3 day), 4, 4, timestamp(date_sub(curdate(), interval 3 day), '10:00:00')),
(111, date_sub(curdate(), interval 2 day), 5, 5, timestamp(date_sub(curdate(), interval 2 day), '10:00:00')),
(111, date_sub(curdate(), interval 1 day), 6, 6, timestamp(date_sub(curdate(), interval 1 day), '10:00:00')),
(111, curdate(), 7, 7, sysdate()),
(112, date_sub(curdate(), interval 3 day), 1, 1, timestamp(date_sub(curdate(), interval 3 day), '09:10:00')),
(112, date_sub(curdate(), interval 2 day), 2, 2, timestamp(date_sub(curdate(), interval 2 day), '09:10:00')),
(112, date_sub(curdate(), interval 1 day), 3, 3, timestamp(date_sub(curdate(), interval 1 day), '09:10:00')),
(115, date_sub(curdate(), interval 4 day), 1, 1, timestamp(date_sub(curdate(), interval 4 day), '21:05:00')),
(115, date_sub(curdate(), interval 3 day), 2, 2, timestamp(date_sub(curdate(), interval 3 day), '21:05:00')),
(118, date_sub(curdate(), interval 4 day), 1, 1, timestamp(date_sub(curdate(), interval 4 day), '20:00:00')),
(118, date_sub(curdate(), interval 3 day), 2, 2, timestamp(date_sub(curdate(), interval 3 day), '20:00:00'));

-- 3、按时间顺序写积分流水，balance 用变量累加，保证每条都对得上

-- 3.1 用户 111：观看课程(8天前) → 观看帖子(7天前) → 6 天签到 → 观看广告(3小时前)
set @bal := 0;
insert into learning_points_log (user_id, change_amount, balance, type, ref_id, remark, create_time)
values (111, 5, @bal := @bal + 5, 'watchCourse', 201, '观看课程小节满 30 秒', timestamp(date_sub(curdate(), interval 8 day), '20:00:00'));
insert into learning_points_log (user_id, change_amount, balance, type, ref_id, remark, create_time)
values (111, 5, @bal := @bal + 5, 'watchPost', 205, '观看帖子满 30 秒', timestamp(date_sub(curdate(), interval 7 day), '19:30:00'));
insert into learning_points_log (user_id, change_amount, balance, type, ref_id, remark, create_time)
select 111, c.points, @bal := @bal + c.points, 'checkin', null,
       concat('每日签到（连续第', c.continuous_days, '天）'), timestamp(c.sign_date, '10:00:00')
  from learning_checkin c
 where c.user_id = 111
 order by c.sign_date;
insert into learning_points_log (user_id, change_amount, balance, type, ref_id, remark, create_time)
values (111, 10, @bal := @bal + 10, 'watchAd', null, '观看广告奖励', date_sub(sysdate(), interval 3 hour));
update learning_points_account set balance = @bal, total_earned = @bal where user_id = 111;

-- 3.2 用户 112：观看课程(7天前) → 3 天签到 → 观看广告(5小时前)
set @bal := 0;
insert into learning_points_log (user_id, change_amount, balance, type, ref_id, remark, create_time)
values (112, 5, @bal := @bal + 5, 'watchCourse', 202, '观看课程小节满 30 秒', timestamp(date_sub(curdate(), interval 7 day), '20:10:00'));
insert into learning_points_log (user_id, change_amount, balance, type, ref_id, remark, create_time)
select 112, c.points, @bal := @bal + c.points, 'checkin', null,
       concat('每日签到（连续第', c.continuous_days, '天）'), timestamp(c.sign_date, '09:10:00')
  from learning_checkin c
 where c.user_id = 112
 order by c.sign_date;
insert into learning_points_log (user_id, change_amount, balance, type, ref_id, remark, create_time)
values (112, 10, @bal := @bal + 10, 'watchAd', null, '观看广告奖励', date_sub(sysdate(), interval 5 hour));
update learning_points_account set balance = @bal, total_earned = @bal where user_id = 112;

-- 3.3 用户 115：2 天签到 → 观看广告(1天前)
set @bal := 0;
insert into learning_points_log (user_id, change_amount, balance, type, ref_id, remark, create_time)
select 115, c.points, @bal := @bal + c.points, 'checkin', null,
       concat('每日签到（连续第', c.continuous_days, '天）'), timestamp(c.sign_date, '21:05:00')
  from learning_checkin c
 where c.user_id = 115
 order by c.sign_date;
insert into learning_points_log (user_id, change_amount, balance, type, ref_id, remark, create_time)
values (115, 10, @bal := @bal + 10, 'watchAd', null, '观看广告奖励', date_sub(sysdate(), interval 1 day));
update learning_points_account set balance = @bal, total_earned = @bal where user_id = 115;

-- 3.4 用户 118：2 天签到
set @bal := 0;
insert into learning_points_log (user_id, change_amount, balance, type, ref_id, remark, create_time)
select 118, c.points, @bal := @bal + c.points, 'checkin', null,
       concat('每日签到（连续第', c.continuous_days, '天）'), timestamp(c.sign_date, '20:00:00')
  from learning_checkin c
 where c.user_id = 118
 order by c.sign_date;
update learning_points_account set balance = @bal, total_earned = @bal where user_id = 118;

-- 4、自检
select user_id, change_amount, balance, type, remark, create_time
  from learning_points_log
 where user_id in (111, 112, 115, 118)
 order by user_id, create_time;

select user_id, balance, total_earned
  from learning_points_account
 where user_id in (111, 112, 115, 118)
 order by user_id;

select user_id, sign_date, points, continuous_days
  from learning_checkin
 where user_id in (111, 112, 115, 118)
 order by user_id, sign_date;

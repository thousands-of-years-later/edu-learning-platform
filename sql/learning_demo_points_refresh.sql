-- ----------------------------
-- 演示数据刷新：清理旧的「兑换免广告」流水，补几条新来源的示例流水
--
-- 背景：积分规则已改为 签到 / 观看课程 / 观看帖子 / 观看广告，
--       旧版本的"兑换免广告"已下线，演示时不应该再出现「兑换」记录。
--
-- 做的事：
--   1) 删除 learning_points_log 里 type='exchange' 的旧流水；
--   2) 把受影响用户的余额回补到"累计获得"（兑换花掉的积分退回）；
--   3) 给演示账号补少量新来源流水：观看课程 / 观看帖子 / 观看广告；
--   4) 同步更新账户余额与累计获得。
--
-- 幂等：插入前按 (user_id, type, ref_id) 判断是否已存在，重复执行不会重复加分。
-- 执行：
--   mysql --default-character-set=utf8mb4 -h43.142.9.214 -P3306 -uedu_dev_03 -p learning_platform < sql/learning_demo_points_refresh.sql
-- ----------------------------

SET NAMES utf8mb4;

-- 1、删除旧兑换流水
delete from learning_points_log where type = 'exchange';

-- 2、余额回补：旧兑换花掉的积分退回（余额 = 累计获得）
update learning_points_account
   set balance = total_earned
 where user_id in (111, 112, 115);

-- 3、演示账号 111：观看课程 +5、观看帖子 +5、观看广告 +10
insert into learning_points_log (user_id, change_amount, balance, type, ref_id, remark, create_time)
select 111, 5, (select balance + 5 from learning_points_account where user_id = 111),
       'watchCourse', 201, '观看课程小节满 30 秒', date_sub(sysdate(), interval 2 day)
  from dual
 where not exists (select 1 from learning_points_log
                    where user_id = 111 and type = 'watchCourse' and ref_id = 201);
set @inserted := row_count();
update learning_points_account
   set balance = balance + 5, total_earned = total_earned + 5
 where user_id = 111 and @inserted = 1;

insert into learning_points_log (user_id, change_amount, balance, type, ref_id, remark, create_time)
select 111, 5, (select balance + 5 from learning_points_account where user_id = 111),
       'watchPost', 205, '观看帖子满 30 秒', date_sub(sysdate(), interval 1 day)
  from dual
 where not exists (select 1 from learning_points_log
                    where user_id = 111 and type = 'watchPost' and ref_id = 205);
set @inserted := row_count();
update learning_points_account
   set balance = balance + 5, total_earned = total_earned + 5
 where user_id = 111 and @inserted = 1;

insert into learning_points_log (user_id, change_amount, balance, type, ref_id, remark, create_time)
select 111, 10, (select balance + 10 from learning_points_account where user_id = 111),
       'watchAd', null, '观看广告奖励', date_sub(sysdate(), interval 3 hour)
  from dual
 where not exists (select 1 from learning_points_log
                    where user_id = 111 and type = 'watchAd');
set @inserted := row_count();
update learning_points_account
   set balance = balance + 10, total_earned = total_earned + 10
 where user_id = 111 and @inserted = 1;

-- 4、演示账号 112：观看课程 +5、观看广告 +10
insert into learning_points_log (user_id, change_amount, balance, type, ref_id, remark, create_time)
select 112, 5, (select balance + 5 from learning_points_account where user_id = 112),
       'watchCourse', 202, '观看课程小节满 30 秒', date_sub(sysdate(), interval 2 day)
  from dual
 where not exists (select 1 from learning_points_log
                    where user_id = 112 and type = 'watchCourse' and ref_id = 202);
set @inserted := row_count();
update learning_points_account
   set balance = balance + 5, total_earned = total_earned + 5
 where user_id = 112 and @inserted = 1;

insert into learning_points_log (user_id, change_amount, balance, type, ref_id, remark, create_time)
select 112, 10, (select balance + 10 from learning_points_account where user_id = 112),
       'watchAd', null, '观看广告奖励', date_sub(sysdate(), interval 5 hour)
  from dual
 where not exists (select 1 from learning_points_log
                    where user_id = 112 and type = 'watchAd');
set @inserted := row_count();
update learning_points_account
   set balance = balance + 10, total_earned = total_earned + 10
 where user_id = 112 and @inserted = 1;

-- 自检
select type, count(*) cnt, sum(change_amount) total
  from learning_points_log
 group by type;
select user_id, balance, total_earned
  from learning_points_account
 where user_id in (111, 112, 115)
 order by user_id;

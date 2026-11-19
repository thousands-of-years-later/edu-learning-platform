-- ----------------------------
-- 违禁词基础词库（30 个，可重复执行）
-- 执行：mysql ... learning_platform < sql/learning_sensitive_word_seed.sql
-- ----------------------------
SET NAMES utf8mb4;
insert ignore into learning_sensitive_word (word, status, remark, create_by, create_time) values ('加微信', '0', '基础词库', 'admin', sysdate());
insert ignore into learning_sensitive_word (word, status, remark, create_by, create_time) values ('加QQ', '0', '基础词库', 'admin', sysdate());
insert ignore into learning_sensitive_word (word, status, remark, create_by, create_time) values ('扫码加群', '0', '基础词库', 'admin', sysdate());
insert ignore into learning_sensitive_word (word, status, remark, create_by, create_time) values ('兼职日结', '0', '基础词库', 'admin', sysdate());
insert ignore into learning_sensitive_word (word, status, remark, create_by, create_time) values ('高薪兼职', '0', '基础词库', 'admin', sysdate());
insert ignore into learning_sensitive_word (word, status, remark, create_by, create_time) values ('刷单返利', '0', '基础词库', 'admin', sysdate());
insert ignore into learning_sensitive_word (word, status, remark, create_by, create_time) values ('点击链接领取', '0', '基础词库', 'admin', sysdate());
insert ignore into learning_sensitive_word (word, status, remark, create_by, create_time) values ('推广返利', '0', '基础词库', 'admin', sysdate());
insert ignore into learning_sensitive_word (word, status, remark, create_by, create_time) values ('赌博', '0', '基础词库', 'admin', sysdate());
insert ignore into learning_sensitive_word (word, status, remark, create_by, create_time) values ('博彩', '0', '基础词库', 'admin', sysdate());
insert ignore into learning_sensitive_word (word, status, remark, create_by, create_time) values ('诈骗', '0', '基础词库', 'admin', sysdate());
insert ignore into learning_sensitive_word (word, status, remark, create_by, create_time) values ('洗钱', '0', '基础词库', 'admin', sysdate());
insert ignore into learning_sensitive_word (word, status, remark, create_by, create_time) values ('套现', '0', '基础词库', 'admin', sysdate());
insert ignore into learning_sensitive_word (word, status, remark, create_by, create_time) values ('代考', '0', '基础词库', 'admin', sysdate());
insert ignore into learning_sensitive_word (word, status, remark, create_by, create_time) values ('代写论文', '0', '基础词库', 'admin', sysdate());
insert ignore into learning_sensitive_word (word, status, remark, create_by, create_time) values ('外挂', '0', '基础词库', 'admin', sysdate());
insert ignore into learning_sensitive_word (word, status, remark, create_by, create_time) values ('破解版', '0', '基础词库', 'admin', sysdate());
insert ignore into learning_sensitive_word (word, status, remark, create_by, create_time) values ('枪支', '0', '基础词库', 'admin', sysdate());
insert ignore into learning_sensitive_word (word, status, remark, create_by, create_time) values ('毒品', '0', '基础词库', 'admin', sysdate());
insert ignore into learning_sensitive_word (word, status, remark, create_by, create_time) values ('迷药', '0', '基础词库', 'admin', sysdate());
insert ignore into learning_sensitive_word (word, status, remark, create_by, create_time) values ('傻逼', '0', '基础词库', 'admin', sysdate());
insert ignore into learning_sensitive_word (word, status, remark, create_by, create_time) values ('脑残', '0', '基础词库', 'admin', sysdate());
insert ignore into learning_sensitive_word (word, status, remark, create_by, create_time) values ('智障', '0', '基础词库', 'admin', sysdate());
insert ignore into learning_sensitive_word (word, status, remark, create_by, create_time) values ('废物', '0', '基础词库', 'admin', sysdate());
insert ignore into learning_sensitive_word (word, status, remark, create_by, create_time) values ('滚蛋', '0', '基础词库', 'admin', sysdate());
insert ignore into learning_sensitive_word (word, status, remark, create_by, create_time) values ('裸聊', '0', '基础词库', 'admin', sysdate());
insert ignore into learning_sensitive_word (word, status, remark, create_by, create_time) values ('约炮', '0', '基础词库', 'admin', sysdate());
insert ignore into learning_sensitive_word (word, status, remark, create_by, create_time) values ('招嫖', '0', '基础词库', 'admin', sysdate());
insert ignore into learning_sensitive_word (word, status, remark, create_by, create_time) values ('传销', '0', '基础词库', 'admin', sysdate());
insert ignore into learning_sensitive_word (word, status, remark, create_by, create_time) values ('发票代开', '0', '基础词库', 'admin', sysdate());
select count(*) as total_words from learning_sensitive_word;
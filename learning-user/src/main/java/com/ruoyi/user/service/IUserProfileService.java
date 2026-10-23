package com.ruoyi.user.service;

import java.util.Map;

/**
 * 用户资料 服务层（编辑主页 / 我的主页）
 *
 * userId 一律由调用方从 API 上下文带入，只能改自己的资料。
 * 校验口径：
 *   昵称 1~20 字（去首尾空格后非空）
 *   简介 ≤200 字，允许清空
 *   性别只认 0/1/2（不公开=2，与 sys_user_sex 字典一致）
 *   头像只接受上传接口返回的地址，允许清空
 *
 * @author edu
 */
public interface IUserProfileService
{
    /**
     * 查询我的资料
     *
     * @param userId 用户ID
     * @return {userId, userName, avatar, sex, sexLabel, bio}
     */
    public Map<String, Object> profile(Long userId);

    /**
     * 保存我的资料（昵称、头像、性别、简介）
     *
     * 两条语句（sys_user + learning_user_profile）在同一事务里，
     * 用户不存在时整笔回滚。
     *
     * @param userId   用户ID
     * @param userName 昵称
     * @param avatar   头像地址（上传接口返回的；传空 = 清空）
     * @param sex      性别 0男 1女 2不公开
     * @param bio      简介（传空 = 清空）
     * @return 保存后的资料（与 profile 同结构，前端可以直接刷新，不用再查一次）
     */
    public Map<String, Object> updateProfile(Long userId, String userName, String avatar, String sex, String bio);

    /**
     * 我的主页首屏聚合数据（设计稿 18_我的主页）
     *
     * 一次请求把这一屏要的都返回，前端不用连着调三个接口：
     * 资料（昵称/头像/性别文案/简介） + 关注数 + 粉丝数 + 获赞数 + 粉丝头像预览。
     *
     * 获赞口径（产品确认）：只算「我发的帖子」的点赞数之和（不含短视频，不限状态，只排除已删除）。
     *
     * @param userId 用户ID
     * @return {userId, userName, avatar, sex, sexLabel, bio,
     *          followCount, fansCount, likeReceivedCount,
     *          fanPreview:[{userId, userName, avatar}]}
     */
    public Map<String, Object> home(Long userId);
}

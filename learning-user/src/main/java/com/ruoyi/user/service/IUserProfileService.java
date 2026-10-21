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
}

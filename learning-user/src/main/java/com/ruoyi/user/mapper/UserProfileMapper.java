package com.ruoyi.user.mapper;

import org.apache.ibatis.annotations.Param;
import com.ruoyi.user.domain.UserProfile;

/**
 * 用户资料 数据层
 *
 * 读：sys_user LEFT JOIN learning_user_profile（一对一扩展表）。
 * 写：昵称/头像/性别落 sys_user，简介落 learning_user_profile —— 两条语句，由服务层放在同一事务里。
 *
 * @author edu
 */
public interface UserProfileMapper
{
    /**
     * 查询用户资料（昵称/头像/性别/简介），用户不存在或已删除返回 null
     *
     * @param userId 用户ID
     * @return 资料（bio 可能为 null）
     */
    public UserProfile selectProfile(@Param("userId") Long userId);

    /**
     * 更新昵称、头像、性别（只能改自己：user_id 由服务层从上下文带入）
     *
     * avatar 允许为 null（表示清空头像），所以 XML 里显式写了 jdbcType。
     *
     * @return 影响行数（0 = 用户不存在或已删除）
     */
    public int updateUserBasic(@Param("userId") Long userId,
                               @Param("userName") String userName,
                               @Param("avatar") String avatar,
                               @Param("sex") String sex);

    /**
     * 写入个人简介：行不存在就建（老用户懒创建），存在就覆盖
     *
     * bio 传 null 表示清空简介，所以 XML 里显式写了 jdbcType。
     *
     * @param userId 用户ID
     * @param bio    简介（null = 清空）
     * @return 影响行数
     */
    public int upsertBio(@Param("userId") Long userId, @Param("bio") String bio);
}

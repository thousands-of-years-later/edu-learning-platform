package com.ruoyi.community.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.ruoyi.community.domain.Follow;

/**
 * 用户关注 数据层
 *
 * 用户存在性校验直接写 SQL 查 sys_user，不反向依赖 ruoyi-system / ISysUserService，
 * 避免模块循环依赖（与 CommentMapper 的 existsPost 等同一思路）。
 *
 * @author edu
 */
public interface FollowMapper
{
    /**
     * 插入一条关注记录
     *
     * 用普通 insert，不用 insert ignore：insert ignore 会把「字段超长、非空违反」
     * 这类真错误也降级成 warning 并返回 0 行，调用方就没法区分「已经关注过」和「插入失败」。
     * 重复关注由唯一索引 uk_user_follow 报出来，服务层捕获 DuplicateKeyException 按幂等处理。
     *
     * @param follow 关注记录（userId + followUserId）
     * @return 影响行数
     */
    public int insertFollow(Follow follow);

    /**
     * 删除一条关注记录（取消关注）
     *
     * 硬删除：删不存在的行返回 0，服务层照样当成功，接口的幂等由此而来。
     *
     * @param userId       关注者用户ID
     * @param followUserId 被关注用户ID
     * @return 影响行数（0 = 本来就没关注，正常情况）
     */
    public int deleteFollow(@Param("userId") Long userId, @Param("followUserId") Long followUserId);

    /**
     * 批量查询当前用户已关注的目标用户ID（供列表页一次性回填关注按钮状态）
     *
     * @param userId        当前用户ID
     * @param targetUserIds 目标用户ID数组
     * @return 其中已被当前用户关注的用户ID集合
     */
    public List<Long> selectFollowedUserIds(@Param("userId") Long userId,
                                            @Param("targetUserIds") Long[] targetUserIds);

    /**
     * 某用户的粉丝数（谁关注了他）
     *
     * @param userId 用户ID
     * @return 粉丝数
     */
    public int countFans(@Param("userId") Long userId);

    /**
     * 某用户关注的人数（他关注了谁）
     *
     * @param userId 用户ID
     * @return 关注数
     */
    public int countFollowing(@Param("userId") Long userId);

    /**
     * 用户是否存在且可用（未停用 + 未删除），返回 0/1
     *
     * 光判 user_id 不够：被停用(status=1)/已删除(del_flag=2)的账号不该再被关注，
     * 否则会出现「关注一个根本登不上的人」。
     *
     * @param userId 用户ID
     * @return 1 = 存在且可用
     */
    public int existsUser(@Param("userId") Long userId);

    /**
     * 我关注的人（分页由控制器 startPage() 驱动）
     *
     * 调用方必须先调 checkQueryUser()，本方法不再查库（见 IFollowService 的说明）。
     *
     * @param userId 要查的用户ID
     * @return 关注记录集合（含展示用户的昵称/头像，展示的是被关注方）
     */
    public List<Follow> selectFollowingList(@Param("userId") Long userId);

    /**
     * 我的粉丝（分页由控制器 startPage() 驱动）
     *
     * 调用方必须先调 checkQueryUser()，本方法不再查库（见 IFollowService 的说明）。
     *
     * @param userId 要查的用户ID
     * @return 关注记录集合（含展示用户的昵称/头像，展示的是关注方）
     */
    public List<Follow> selectFansList(@Param("userId") Long userId);
}

package com.ruoyi.community.service;

import java.util.List;
import java.util.Map;
import com.ruoyi.community.domain.Follow;

/**
 * 用户关注 服务层
 *
 * @author edu
 */
public interface IFollowService
{
    /**
     * 关注某人（幂等：已关注时重复调用也成功）
     *
     * @param userId       关注者用户ID（由控制器从登录态取，绝不接受前端传入）
     * @param targetUserId 被关注用户ID
     * @return {active: true, fansCount: 对方操作后的真实粉丝数}
     */
    public Map<String, Object> addFollow(Long userId, Long targetUserId);

    /**
     * 取消关注（硬删除，幂等：本来就没关注也成功）
     *
     * @param userId       操作人用户ID（由控制器从登录态取）
     * @param targetUserId 被取消关注的用户ID
     * @return {active: false, fansCount: 对方操作后的真实粉丝数}
     */
    public Map<String, Object> cancelFollow(Long userId, Long targetUserId);

    /**
     * 批量查询当前用户已关注的目标用户ID（供列表页一次性回填关注按钮状态）
     *
     * @param userId        当前用户ID
     * @param targetUserIds 目标用户ID数组
     * @return 其中已被关注的用户ID集合
     */
    public List<Long> selectFollowedUserIds(Long userId, Long[] targetUserIds);

    /**
     * 查某用户的粉丝数 + 关注数
     *
     * @param queryUserId 要查的用户ID（可以是别人）
     * @return {fansCount: 粉丝数, followCount: 关注数}
     */
    public Map<String, Object> selectCounts(Long queryUserId);

    /**
     * 校验要查的用户存在且可用（未停用 + 未删除），不合法直接抛 ServiceException
     *
     * 【必须在控制器的 startPage() 之前调用】：PageHelper 的分页参数挂在 ThreadLocal 上，
     * 会被它之后的【第一条】查询消费掉 —— 本方法里就有查询（existsUser），
     * 先开分页会让分页作用在存在性校验上，真正要分页的列表查询反而没有 LIMIT。
     *
     * @param queryUserId 要查的用户ID
     */
    public void checkQueryUser(Long queryUserId);

    /**
     * 我关注的人（分页由控制器 startPage() 驱动）
     *
     * 调用方必须先调 checkQueryUser()，本方法只做参数校验，不再查库（见 checkQueryUser 的说明）。
     *
     * @param queryUserId 要查的用户ID
     * @return 关注记录集合（含展示用户的昵称/头像，展示的是被关注方）
     */
    public List<Follow> selectFollowingList(Long queryUserId);

    /**
     * 我的粉丝（分页由控制器 startPage() 驱动）
     *
     * 调用方必须先调 checkQueryUser()，本方法只做参数校验，不再查库。
     *
     * @param queryUserId 要查的用户ID
     * @return 关注记录集合（含展示用户的昵称/头像，展示的是关注方）
     */
    public List<Follow> selectFansList(Long queryUserId);
}

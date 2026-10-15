package com.ruoyi.community.service.impl;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.community.domain.Follow;
import com.ruoyi.community.mapper.FollowMapper;
import com.ruoyi.community.service.IFollowService;
import com.ruoyi.interaction.service.INotificationService;

/**
 * 用户关注 服务层实现
 *
 * 幂等的关键点：
 *   1. 关注 = 普通 insert，重复关注靠唯一索引 uk_user_follow 报 DuplicateKeyException，
 *      捕获后【按成功返回】—— 本接口只表达「关注」这一个意图（add / cancel 两个幂等接口，
 *      不做 toggle），重复就等于「已经是目标状态」，客户端双击、网络重试结果都一样。
 *      不用「先查后插」：两次查询之间有竞态，唯一索引才是权威判定
 *      （与 CourseRatingServiceImpl.addRating 同一思路）。
 *   2. 取关 = 硬删除，删 0 行也返回成功（本来就没关注）。
 *   3. 粉丝数一律现算 count(*)，本表没有也不需要任何计数列。
 *
 * @author edu
 */
@Service
public class FollowServiceImpl implements IFollowService
{
    /**
     * 一次批量查询最多允许的用户数
     *
     * 前端是「一页内容回填关注按钮状态」，几十个就够了；
     * 与 InteractionServiceImpl.MAX_QUERY_IDS 同一数值、同一理由：
     * 不设上限的话客户端能一次传几千个 id，拼出一条超长 in (...) 把库拖慢。
     */
    private static final int MAX_QUERY_IDS = 200;

    @Autowired
    private FollowMapper followMapper;

    @Autowired
    private INotificationService notificationService;

    @Override
    @Transactional
    public Map<String, Object> addFollow(Long userId, Long targetUserId)
    {
        // 参数校验，顺序固定：登录 → 目标ID → 自己 → 存在
        if (userId == null)
        {
            throw new ServiceException("请先登录");
        }
        if (targetUserId == null)
        {
            throw new ServiceException("用户ID不能为空");
        }
        if (targetUserId.equals(userId))
        {
            throw new ServiceException("不能关注自己");
        }
        if (followMapper.existsUser(targetUserId) == 0)
        {
            throw new ServiceException("用户不存在或已被停用");
        }

        Follow follow = new Follow();
        follow.setUserId(userId);
        follow.setFollowUserId(targetUserId);
        boolean created = false;
        try
        {
            followMapper.insertFollow(follow);
            created = true;
        }
        catch (DuplicateKeyException e)
        {
            // 唯一索引 uk_user_follow 报重复 = 已经关注过，这次是重复调用，按幂等放过。
            // 之所以靠捕获异常而不是 insert ignore：后者会把其它插入错误也当成「已关注」，
            // 那种情况下关注会静默失效却回报成功。
            // 在事务里捕获 DuplicateKeyException 是安全的（回滚标记只在异常穿过代理边界时才打），
            // 与 InteractionServiceImpl.toggle 的做法相同。
        }

        if (created)
        {
            // 只有真正新增关注才通知被关注者；重复关注是幂等重放，不该反复打扰对方。
            // 取消关注不通知、也不删历史通知（MVP 取舍）。与本次关注同事务
            notificationService.notifyFollow(userId, targetUserId);
        }

        Map<String, Object> result = new HashMap<String, Object>();
        result.put("active", true);
        result.put("fansCount", followMapper.countFans(targetUserId));
        return result;
    }

    @Override
    @Transactional
    public Map<String, Object> cancelFollow(Long userId, Long targetUserId)
    {
        if (userId == null)
        {
            throw new ServiceException("请先登录");
        }
        if (targetUserId == null)
        {
            throw new ServiceException("用户ID不能为空");
        }
        if (targetUserId.equals(userId))
        {
            throw new ServiceException("不能取消关注自己");
        }

        // 刻意【不校验被关注用户是否还存在】：账号被停用/删除后这条关注关系仍留在关注列表里，
        // 客户端要能把它取消掉；而且取消本来就是「删不存在的行也算成功」的幂等操作
        // （与 CommentServiceImpl.deleteComment 忽略影响行数同一取舍）。
        followMapper.deleteFollow(userId, targetUserId);

        Map<String, Object> result = new HashMap<String, Object>();
        result.put("active", false);
        result.put("fansCount", followMapper.countFans(targetUserId));
        return result;
    }

    @Override
    public List<Long> selectFollowedUserIds(Long userId, Long[] targetUserIds)
    {
        if (userId == null || targetUserIds == null || targetUserIds.length == 0)
        {
            return Collections.emptyList();
        }
        if (targetUserIds.length > MAX_QUERY_IDS)
        {
            throw new ServiceException("一次最多查询 " + MAX_QUERY_IDS + " 个用户的关注状态");
        }
        return followMapper.selectFollowedUserIds(userId, targetUserIds);
    }

    @Override
    public Map<String, Object> selectCounts(Long queryUserId)
    {
        checkQueryUser(queryUserId);

        Map<String, Object> result = new HashMap<String, Object>();
        result.put("fansCount", followMapper.countFans(queryUserId));
        result.put("followCount", followMapper.countFollowing(queryUserId));
        return result;
    }

    @Override
    public void checkQueryUser(Long queryUserId)
    {
        if (queryUserId == null)
        {
            throw new ServiceException("用户ID不能为空");
        }
        if (followMapper.existsUser(queryUserId) == 0)
        {
            // 不存在的用户直接报错，不返回 0：否则能拿统计/列表接口逐个探测哪些 ID 是真实用户
            // （与 InteractionServiceImpl 的目标存在性校验同一原则）。
            throw new ServiceException("用户不存在或已被停用");
        }
    }

    @Override
    public List<Follow> selectFollowingList(Long queryUserId)
    {
        // 这里【只做参数校验，不查库】：控制器是在 startPage() 之后调本方法的，
        // 而 PageHelper 的分页参数挂在 ThreadLocal 上、会被其后第一条查询消费掉 ——
        // 若在这里查一次库，分页就会作用在那条查询上，真正的列表查询反而没有 LIMIT
        // （表现是分页静默失效、一次返回全量）。存在性校验因此拆到了 checkQueryUser()，
        // 由控制器在 startPage() 之前调用。
        if (queryUserId == null)
        {
            throw new ServiceException("用户ID不能为空");
        }
        return followMapper.selectFollowingList(queryUserId);
    }

    @Override
    public List<Follow> selectFansList(Long queryUserId)
    {
        // 说明同 selectFollowingList：只做参数校验，不查库
        if (queryUserId == null)
        {
            throw new ServiceException("用户ID不能为空");
        }
        return followMapper.selectFansList(queryUserId);
    }
}

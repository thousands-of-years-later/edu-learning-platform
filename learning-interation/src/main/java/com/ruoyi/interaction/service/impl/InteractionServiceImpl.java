package com.ruoyi.interaction.service.impl;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.interaction.domain.Interaction;
import com.ruoyi.interaction.mapper.InteractionMapper;
import com.ruoyi.interaction.service.IInteractionService;
import com.ruoyi.interaction.service.INotificationService;

/**
 * 点赞收藏 服务层实现
 *
 * 计数维护的关键点：
 *   1. 先写关联表，再按【影响行数】决定要不要动计数 —— 这是幂等的来源
 *      （重复点赞时 insert ignore 返回 0，计数不动）
 *   2. 计数用 like_count = like_count + 1 的原子写法，不做"读出来加一再写回"
 *   3. 整个操作在一个事务里
 *
 * @author edu
 */
@Service
public class InteractionServiceImpl implements IInteractionService
{
    /**
     * 一次批量查询最多允许的目标数
     *
     * 前端是「一页内容回填按钮状态」，几十个就够了；
     * 不设上限的话客户端能一次传几千个 id，拼出一条超长 in (...) 把库拖慢。
     */
    private static final int MAX_QUERY_IDS = 200;

    @Autowired
    private InteractionMapper interactionMapper;

    @Autowired
    private INotificationService notificationService;

    @Override
    @Transactional
    public Map<String, Object> toggle(Long userId, String targetType, Long targetId, String type)
    {
        if (userId == null)
        {
            throw new ServiceException("请先登录");
        }
        if (!Interaction.isValidTargetType(targetType))
        {
            throw new ServiceException("目标类型不合法");
        }
        if (!Interaction.isValidType(type))
        {
            throw new ServiceException("操作类型不合法");
        }
        if (targetId == null)
        {
            throw new ServiceException("目标ID不能为空");
        }
        if (!targetExists(targetType, targetId))
        {
            throw new ServiceException("内容不存在或已被删除");
        }

        Interaction entity = new Interaction();
        entity.setUserId(userId);
        entity.setTargetType(targetType);
        entity.setTargetId(targetId);
        entity.setType(type);

        boolean active;
        int delta;
        try
        {
            interactionMapper.insertOne(entity);
            // 插入成功 = 之前没点过，这次点上了
            active = true;
            delta = 1;
        }
        catch (DuplicateKeyException e)
        {
            // 唯一索引 uk_user_target 报重复 = 已经点过，这次是取消。
            // 之所以靠捕获异常而不是 insert ignore：后者会把其它插入错误也当成「已点过」，
            // 那种情况下点赞会静默失效、还顺手把计数减掉。
            // 并发下可能已被别处删掉，那就不要再减计数
            int deleted = interactionMapper.deleteOne(entity);
            active = false;
            delta = deleted > 0 ? -1 : 0;
        }

        if (active)
        {
            // 新增点赞/收藏才通知内容归属人（课程无归属人、自赞自藏由通知服务内部跳过）。
            // 刻意放在 try/catch 【外面】：通知插入若报重复键之类的异常，不能被上面的
            // catch 当成「已点过、这次是取消」吞掉。
            // 取消不通知、也不删历史通知；与本次点赞同事务，写失败一并回滚
            notificationService.notifyInteraction(userId, targetType, targetId, type);
        }

        if (delta != 0)
        {
            int updated = interactionMapper.updateCount(tableOf(targetType), idColumnOf(targetType), targetId,
                    columnOf(type), delta);
            // 影响 0 行有两种可能：内容在「存在性校验」之后又被删了，或者计数本来就到 0 减不动
            // （greatest 兜底）。前者必须报错回滚，否则关联表多一条孤儿记录、计数永久漂移，
            // 只能靠 learning_interaction_fix_count.sql 对账；后者是正常情况，放过。
            if (updated == 0 && !targetExists(targetType, targetId))
            {
                throw new ServiceException("内容不存在或已被删除");
            }
        }

        Map<String, Object> result = new HashMap<String, Object>();
        result.put("active", active);
        result.put("count", interactionMapper.countByTarget(targetType, targetId, type));
        return result;
    }

    @Override
    public List<Long> activeIds(Long userId, String targetType, String type, Long[] ids)
    {
        if (userId == null || ids == null || ids.length == 0)
        {
            return java.util.Collections.emptyList();
        }
        if (!Interaction.isValidTargetType(targetType) || !Interaction.isValidType(type))
        {
            throw new ServiceException("参数不合法");
        }
        if (ids.length > MAX_QUERY_IDS)
        {
            throw new ServiceException("一次最多查询 " + MAX_QUERY_IDS + " 个目标的互动状态");
        }
        return interactionMapper.selectActiveTargetIds(userId, targetType, type, ids);
    }

    @Override
    public int count(String targetType, Long targetId, String type)
    {
        if (targetId == null || !Interaction.isValidTargetType(targetType) || !Interaction.isValidType(type))
        {
            throw new ServiceException("参数不合法");
        }
        return interactionMapper.countByTarget(targetType, targetId, type);
    }

    /** 目标是否存在（未逻辑删除） */
    private boolean targetExists(String targetType, Long targetId)
    {
        if (Interaction.TARGET_POST.equals(targetType))
        {
            return interactionMapper.existsPost(targetId) > 0;
        }
        if (Interaction.TARGET_COURSE.equals(targetType))
        {
            return interactionMapper.existsCourse(targetId) > 0;
        }
        return interactionMapper.existsVideo(targetId) > 0;
    }

    /**
     * 目标类型 -> 计数所在的表
     * 只返回常量，不接受外部输入
     */
    private String tableOf(String targetType)
    {
        if (Interaction.TARGET_POST.equals(targetType))
        {
            return "learning_post";
        }
        if (Interaction.TARGET_COURSE.equals(targetType))
        {
            return "learning_course";
        }
        return "learning_short_video";
    }

    /**
     * 目标类型 -> 主键列名（三张表的主键名都不一样）
     */
    private String idColumnOf(String targetType)
    {
        if (Interaction.TARGET_POST.equals(targetType))
        {
            return "post_id";
        }
        if (Interaction.TARGET_COURSE.equals(targetType))
        {
            return "course_id";
        }
        return "video_id";
    }

    /**
     * 行为类型 -> 计数列名
     * 只返回常量，不接受外部输入
     */
    private String columnOf(String type)
    {
        return Interaction.TYPE_FAVORITE.equals(type) ? "favorite_count" : "like_count";
    }
}
package com.ruoyi.interaction.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.ruoyi.interaction.domain.MyInteraction;

/**
 * 「我的点赞 / 我的收藏」查询（不新增表，直接查 learning_interaction）
 *
 * 标题/封面/作者用标量子查询从三张内容表现取，不 join：
 * 本模块只依赖 ruoyi-common，反向依赖 learning-course / learning-video 会造成循环依赖
 * （与 NoteMapper / WatchHistoryMapper 同一写法）。
 *
 * @author edu
 */
public interface MyInteractionMapper
{
    /**
     * 查我的点赞/收藏列表（分页由上层 PageHelper 驱动，按时间倒序）
     *
     * 只返回【内容仍然可见】的条目（未删 + 状态正常）——
     * 内容被删后互动记录本来就由内容模块级联清掉了，这里再兜一层，
     * 避免历史孤儿数据出现在列表里、点进去却打不开。
     *
     * @param userId     用户ID
     * @param actionType 行为类型：1 点赞 / 2 收藏（互动表的 char(1) 编码）
     * @param targetType 内容类型：1 帖子 / 2 课程 / 3 短视频；null = 全部
     * @return 列表项（title / coverUrl / author 已填充）
     */
    public List<MyInteraction> selectMyList(@Param("userId") Long userId,
                                           @Param("actionType") String actionType,
                                           @Param("targetType") String targetType);
}

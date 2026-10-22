package com.ruoyi.interaction.service;

import java.util.List;
import java.util.Map;
import com.ruoyi.interaction.domain.MyInteraction;

/**
 * 点赞收藏 服务层
 *
 * @author edu
 */
public interface IInteractionService
{
    /**
     * 点赞/取消 或 收藏/取消（同一个入口）
     *
     * @param userId     用户ID（由控制器从登录态取，绝不接受前端传入）
     * @param targetType 目标类型（1帖子 2课程 3短视频）
     * @param targetId   目标ID
     * @param type       行为类型（1点赞 2收藏）
     * @return {active: 操作后是否已点赞/已收藏, count: 操作后的真实数量}
     */
    public Map<String, Object> toggle(Long userId, String targetType, Long targetId, String type);

    /**
     * 批量查询当前用户已点赞/已收藏的目标ID（供列表页一次性回填按钮状态）
     *
     * @param userId     用户ID
     * @param targetType 目标类型
     * @param type       行为类型
     * @param ids        目标ID数组
     * @return 目标ID集合
     */
    public List<Long> activeIds(Long userId, String targetType, String type, Long[] ids);

    /**
     * 查某个目标的真实互动数（累计值，不区分是谁点的）
     *
     * @param targetType 目标类型（1帖子 2课程 3短视频）
     * @param targetId   目标ID
     * @param type       行为类型（1点赞 2收藏）
     * @return 数量
     */
    public int count(String targetType, Long targetId, String type);

    /**
     * 我的点赞列表（设计稿 23_我的点赞）
     *
     * 只返回内容仍然可见的条目；标题/封面/作者由查询时现取（文章无封面、有作者）。
     *
     * @param userId     用户ID
     * @param targetType 可选过滤：post / course / shortVideo；空 = 全部
     * @return 列表项（分页由上层 PageHelper 驱动，按点赞时间倒序）
     */
    public List<MyInteraction> myLikes(Long userId, String targetType);

    /**
     * 我的收藏列表（设计稿 22_我的收藏），口径同 myLikes
     *
     * @param userId     用户ID
     * @param targetType 可选过滤：post / course / shortVideo；空 = 全部
     * @return 列表项（按收藏时间倒序）
     */
    public List<MyInteraction> myFavorites(Long userId, String targetType);

    /**
     * 校验列表的类型过滤参数（必须在 PageHelper 的 startPage() 之前调用）
     *
     * 传了非法类型直接报错，不静默当成"全部"——否则前端传错会看到全部数据，
     * 以为 tab 过滤没生效。
     */
    public void checkQuery(String targetType);
}
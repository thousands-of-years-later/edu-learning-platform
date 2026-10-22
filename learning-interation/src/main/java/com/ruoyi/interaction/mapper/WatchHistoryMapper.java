package com.ruoyi.interaction.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.ruoyi.interaction.domain.WatchHistory;

/**
 * 观看历史 数据层
 *
 * 标题/封面/小节标题都用【标量子查询】取，不 join 内容表：
 * 本模块只依赖 ruoyi-common，反向依赖 learning-course / learning-video 会形成循环依赖
 * （与 NoteMapper / CommentMapper 同一思路）。
 *
 * 写入采用「先 insert，撞唯一索引再 update」：一条内容只留一行，同时天然防并发重复插入。
 *
 * @author edu
 */
public interface WatchHistoryMapper
{
    /**
     * 插入一条观看历史（historyId 由 useGeneratedKeys 回填）
     *
     * 撞唯一索引 uk_history_user_target 会抛 DuplicateKeyException，
     * 服务层据此转去 updateHistory（重看只更新时间与进度）。
     */
    public int insertHistory(WatchHistory history);

    /**
     * 更新已有历史：进度【跟随最新】直接覆盖，同时刷新最近观看时间
     *
     * @return 影响行数（1 = 更新成功）
     */
    public int updateHistory(WatchHistory history);

    /**
     * 查某条内容的历史（断点续播用），没有返回 null
     */
    public WatchHistory selectOne(@Param("userId") Long userId,
                                  @Param("targetType") String targetType,
                                  @Param("targetId") Long targetId);

    /**
     * 我的历史列表（分页由上层 PageHelper 驱动，按最近观看时间倒序）
     *
     * @param userId     用户ID（必填）
     * @param targetType 可选过滤：shortVideo / course / post
     */
    public List<WatchHistory> selectList(@Param("userId") Long userId,
                                        @Param("targetType") String targetType);

    /**
     * 删除一条历史（只能删自己的：user_id 进 where）
     */
    public int deleteById(@Param("historyId") Long historyId, @Param("userId") Long userId);

    /**
     * 按类型清空我的历史（前端是三个 tab，各自清自己那一类）
     *
     * 不提供"清空全部"：前端少传一个类型参数就会把三类历史一起清掉，风险太大。
     */
    public int deleteByType(@Param("userId") Long userId, @Param("targetType") String targetType);

    /**
     * 短视频是否存在且可看（未删 + 状态正常）
     */
    public int countShortVideo(@Param("targetId") Long targetId);

    /**
     * 课程是否存在且可看
     */
    public int countCourse(@Param("targetId") Long targetId);

    /**
     * 文章（帖子）是否存在且可看：帖子 status='0' 才是已发布
     */
    public int countPost(@Param("targetId") Long targetId);

    /**
     * 某个小节是否属于该课程（防止上报 A 课程却带 B 课程的小节ID）
     */
    public int countCourseVideoOfCourse(@Param("videoId") Long videoId, @Param("courseId") Long courseId);

    /**
     * 取短视频的时长（内容表里的时长比客户端上报更可信，优先用它）
     */
    public Integer selectShortVideoDuration(@Param("targetId") Long targetId);

    /**
     * 短视频播放数 +1（只有短视频表有 view_count；首次记录时调用）
     */
    public int increaseShortVideoViewCount(@Param("targetId") Long targetId);
}

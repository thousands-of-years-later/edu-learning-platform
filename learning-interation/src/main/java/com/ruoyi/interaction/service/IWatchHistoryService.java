package com.ruoyi.interaction.service;

import java.util.List;
import java.util.Map;
import com.ruoyi.interaction.domain.WatchHistory;

/**
 * 观看历史 服务层
 *
 * 三种内容（短视频 / 课程 / 文章）共用一张表与一套接口：
 *   shortVideo 有进度（秒），课程按【课程】粒度一条并记住上次的小节，文章只记"点开过"。
 * 进度口径是【跟随最新】：重看时覆盖，不取最大值。
 *
 * userId 一律由调用方从 API 上下文带入，只能读/删自己的历史。
 *
 * @author edu
 */
public interface IWatchHistoryService
{
    /**
     * 上报观看（首次插入，之后每次覆盖进度与时间）
     *
     * @param userId     用户ID
     * @param targetType shortVideo / course / post
     * @param targetId   短视频ID / 课程ID / 帖子ID
     * @param videoId    课程专用：本次观看的小节ID（其它类型传了也会被忽略）
     * @param position   视频：看到第几秒（文章忽略）
     * @param duration   视频：总时长（短视频以内容表为准，课程小节用上报值）
     * @return {historyId, targetType, targetId, videoId, position, duration, firstTime}
     */
    public Map<String, Object> report(Long userId, String targetType, Long targetId,
                                      Long videoId, Integer position, Integer duration);

    /**
     * 查断点（播放页进入时调）：返回上次看到的小节与秒数
     *
     * @return {found, videoId, position, duration, lastViewTime}
     */
    public Map<String, Object> progress(Long userId, String targetType, Long targetId);

    /**
     * 我的观看历史（分页由上层 PageHelper 驱动，按最近观看时间倒序）
     *
     * @param targetType 可选过滤：shortVideo / course / post
     */
    public List<WatchHistory> list(Long userId, String targetType);

    /**
     * 删除一条历史（只能删自己的）
     *
     * @return 影响行数（恒为 1，0 行会抛业务异常）
     */
    public int remove(Long userId, Long historyId);

    /**
     * 清空我的全部历史
     *
     * @return 实际删除的行数（0 = 本来就没历史，不算错误）
     */
    public int clear(Long userId);

    /**
     * 校验列表过滤参数（必须在 PageHelper 的 startPage() 之前调用）
     */
    public void checkQuery(String targetType);
}

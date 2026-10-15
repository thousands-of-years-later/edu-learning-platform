package com.ruoyi.video.mapper;

import java.util.List;
import com.ruoyi.video.domain.ShortVideo;

/**
 * 短视频 数据层
 *
 * @author edu
 */
public interface ShortVideoMapper
{
    /**
     * 查询短视频列表
     *
     * @param shortVideo 短视频信息
     * @return 短视频集合
     */
    public List<ShortVideo> selectShortVideoList(ShortVideo shortVideo);

    /**
     * 根据视频ID查询短视频（只查未删除的）
     *
     * @param videoId 视频ID
     * @return 短视频信息
     */
    public ShortVideo selectShortVideoById(Long videoId);

    /**
     * 校验视频标题是否唯一（只查未删除的）
     *
     * @param shortVideo 短视频信息（videoTitle 必传）
     * @return 短视频信息
     */
    public ShortVideo checkVideoTitleUnique(ShortVideo shortVideo);

    /**
     * 新增短视频
     *
     * @param shortVideo 短视频信息
     * @return 结果
     */
    public int insertShortVideo(ShortVideo shortVideo);

    /**
     * 修改短视频
     *
     * @param shortVideo 短视频信息
     * @return 结果
     */
    public int updateShortVideo(ShortVideo shortVideo);

    /**
     * 批量逻辑删除短视频，并清零 like_count / favorite_count / comment_count
     *
     * @param videoIds 需要删除的视频ID
     * @return 结果
     */
    public int deleteShortVideoByIds(Long[] videoIds);

    /**
     * 删除指向这些短视频的点赞收藏（级联清理，硬删除）
     *
     * learning_interaction 在 learning-interation 模块，本模块只依赖 ruoyi-common，
     * 反向注入会形成循环依赖，按 CategoryMapper.countPostByCategoryId 的先例直接写纯 SQL。
     * 该表没有 del_flag（取消点赞即硬删除），这里同样是硬删除。
     * target_type 必须带上：三种目标的 ID 各自独立编号，只按 target_id 过滤会串到别的类型上。
     *
     * @param videoIds 被删除的短视频ID
     * @return 结果
     */
    public int deleteInteractionByVideoIds(Long[] videoIds);

    /**
     * 逻辑删除指向这些短视频的评论（级联清理）
     *
     * 同上走纯 SQL。learning_comment 与内容表一致用 del_flag 软删除，
     * where 带 del_flag = '0'，重复执行不会反复改写 update_time。
     *
     * @param videoIds 被删除的短视频ID
     * @return 结果
     */
    public int softDeleteCommentByVideoIds(Long[] videoIds);
}
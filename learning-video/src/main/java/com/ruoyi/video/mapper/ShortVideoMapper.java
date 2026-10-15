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
     * 批量逻辑删除短视频
     *
     * @param videoIds 需要删除的视频ID
     * @return 结果
     */
    public int deleteShortVideoByIds(Long[] videoIds);
}
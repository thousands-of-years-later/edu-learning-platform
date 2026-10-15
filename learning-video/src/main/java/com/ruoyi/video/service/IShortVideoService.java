package com.ruoyi.video.service;

import java.util.List;
import com.ruoyi.video.domain.ShortVideo;

/**
 * 短视频 服务层
 *
 * @author edu
 */
public interface IShortVideoService
{
    /**
     * 查询短视频列表
     *
     * @param shortVideo 短视频信息
     * @return 短视频集合
     */
    public List<ShortVideo> selectShortVideoList(ShortVideo shortVideo);

    /**
     * 根据视频ID查询短视频
     *
     * @param videoId 视频ID
     * @return 短视频信息
     */
    public ShortVideo selectShortVideoById(Long videoId);

    /**
     * 校验视频标题是否唯一
     *
     * @param shortVideo 短视频信息
     * @return 结果
     */
    public boolean checkVideoTitleUnique(ShortVideo shortVideo);

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
     * @param ids 需要删除的数据ID
     * @return 结果
     */
    public int deleteShortVideoByIds(String ids);
}
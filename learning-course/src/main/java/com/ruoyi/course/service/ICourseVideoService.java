package com.ruoyi.course.service;

import java.util.List;
import com.ruoyi.course.domain.CourseVideo;

/**
 * 课程视频 服务层
 *
 * @author edu
 */
public interface ICourseVideoService
{
    /**
     * 查询视频列表
     *
     * @param video 视频信息（courseId 用于限定某个课程）
     * @return 视频集合
     */
    public List<CourseVideo> selectVideoList(CourseVideo video);

    /**
     * 根据视频ID查询视频
     *
     * @param videoId 视频ID
     * @return 视频信息
     */
    public CourseVideo selectVideoById(Long videoId);

    /**
     * 新增视频
     *
     * @param video 视频信息
     * @return 结果
     */
    public int insertVideo(CourseVideo video);

    /**
     * 修改视频
     *
     * @param video 视频信息
     * @return 结果
     */
    public int updateVideo(CourseVideo video);

    /**
     * 批量逻辑删除视频
     *
     * @param ids 需要删除的数据ID
     * @return 结果
     */
    public int deleteVideoByIds(String ids);
}

package com.ruoyi.course.mapper;

import java.util.List;
import com.ruoyi.course.domain.CourseVideo;

/**
 * 课程视频 数据层
 *
 * @author edu
 */
public interface CourseVideoMapper
{
    /**
     * 查询视频列表（关联查出所属章节标题）
     *
     * @param video 视频信息（courseId 用于限定某个课程）
     * @return 视频集合
     */
    public List<CourseVideo> selectVideoList(CourseVideo video);

    /**
     * 根据视频ID查询视频（只查未删除的）
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
     * @param videoIds 需要删除的视频ID
     * @return 结果
     */
    public int deleteVideoByIds(Long[] videoIds);

    /**
     * 按课程批量逻辑删除视频（删除课程时级联）
     *
     * @param courseIds 课程ID
     * @return 结果
     */
    public int deleteVideoByCourseIds(Long[] courseIds);

    /**
     * 按章节批量逻辑删除视频（删除章节时级联）
     *
     * @param chapterIds 章节ID
     * @return 结果
     */
    public int deleteVideoByChapterIds(Long[] chapterIds);
}

package com.ruoyi.course.service;

import java.util.List;
import com.ruoyi.course.domain.CourseChapter;

/**
 * 课程章节 服务层
 *
 * @author edu
 */
public interface ICourseChapterService
{
    /**
     * 查询章节列表
     *
     * @param chapter 章节信息（courseId 用于限定某个课程）
     * @return 章节集合
     */
    public List<CourseChapter> selectChapterList(CourseChapter chapter);

    /**
     * 根据章节ID查询章节
     *
     * @param chapterId 章节ID
     * @return 章节信息
     */
    public CourseChapter selectChapterById(Long chapterId);

    /**
     * 新增章节
     *
     * @param chapter 章节信息
     * @return 结果
     */
    public int insertChapter(CourseChapter chapter);

    /**
     * 修改章节
     *
     * @param chapter 章节信息
     * @return 结果
     */
    public int updateChapter(CourseChapter chapter);

    /**
     * 批量逻辑删除章节，同时级联删除章节下的视频
     *
     * @param ids 需要删除的数据ID
     * @return 结果
     */
    public int deleteChapterByIds(String ids);
}

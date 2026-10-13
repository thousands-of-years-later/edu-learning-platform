package com.ruoyi.course.mapper;

import java.util.List;
import com.ruoyi.course.domain.CourseChapter;

/**
 * 课程章节 数据层
 *
 * @author edu
 */
public interface CourseChapterMapper
{
    /**
     * 查询章节列表
     *
     * @param chapter 章节信息（courseId 用于限定某个课程）
     * @return 章节集合
     */
    public List<CourseChapter> selectChapterList(CourseChapter chapter);

    /**
     * 根据章节ID查询章节（只查未删除的）
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
     * 批量逻辑删除章节
     *
     * @param chapterIds 需要删除的章节ID
     * @return 结果
     */
    public int deleteChapterByIds(Long[] chapterIds);

    /**
     * 按课程批量逻辑删除章节（删除课程时级联）
     *
     * @param courseIds 课程ID
     * @return 结果
     */
    public int deleteChapterByCourseIds(Long[] courseIds);
}

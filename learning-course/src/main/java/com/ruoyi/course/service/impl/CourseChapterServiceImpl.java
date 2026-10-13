package com.ruoyi.course.service.impl;

import java.util.List;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ruoyi.common.core.text.Convert;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.course.domain.Course;
import com.ruoyi.course.domain.CourseChapter;
import com.ruoyi.course.mapper.CourseChapterMapper;
import com.ruoyi.course.mapper.CourseMapper;
import com.ruoyi.course.mapper.CourseVideoMapper;
import com.ruoyi.course.service.ICourseChapterService;

/**
 * 课程章节 服务层处理
 *
 * @author edu
 */
@Service
public class CourseChapterServiceImpl implements ICourseChapterService
{
    @Autowired
    private CourseChapterMapper courseChapterMapper;

    @Autowired
    private CourseMapper courseMapper;

    @Autowired
    private CourseVideoMapper courseVideoMapper;

    @Override
    public List<CourseChapter> selectChapterList(CourseChapter chapter)
    {
        return courseChapterMapper.selectChapterList(chapter);
    }

    @Override
    public CourseChapter selectChapterById(Long chapterId)
    {
        return courseChapterMapper.selectChapterById(chapterId);
    }

    @Override
    public int insertChapter(CourseChapter chapter)
    {
        chapter.setChapterTitle(trimChapterTitle(chapter.getChapterTitle()));
        checkCourseExists(chapter.getCourseId());
        return courseChapterMapper.insertChapter(chapter);
    }

    @Override
    public int updateChapter(CourseChapter chapter)
    {
        chapter.setChapterTitle(trimChapterTitle(chapter.getChapterTitle()));
        CourseChapter old = courseChapterMapper.selectChapterById(chapter.getChapterId());
        if (StringUtils.isNull(old))
        {
            throw new ServiceException("章节不存在或已被删除");
        }
        // 章节不允许搬家到别的课程：章节和视频的归属关系必须保持一致
        if (!Objects.equals(old.getCourseId(), chapter.getCourseId()))
        {
            chapter.setCourseId(old.getCourseId());
        }
        return courseChapterMapper.updateChapter(chapter);
    }

    /**
     * 删除章节时连视频一起删：删章节 = 删掉这一节的内容。
     * 两张表同进同退，避免留下「章节已删、视频还挂在空章节下」的数据。
     */
    @Override
    @Transactional
    public int deleteChapterByIds(String ids)
    {
        Long[] chapterIds = Convert.toLongArray(ids);
        // 空串会被 Convert 转成空数组，<foreach> 将拼出 in () 造成 SQL 语法错误，必须拦截
        if (chapterIds == null || chapterIds.length == 0)
        {
            throw new ServiceException("未选择要删除的章节数据");
        }
        for (Long chapterId : chapterIds)
        {
            CourseChapter chapter = courseChapterMapper.selectChapterById(chapterId);
            if (StringUtils.isNull(chapter))
            {
                throw new ServiceException(String.format("章节ID[%s]不存在或已被删除", chapterId));
            }
        }
        courseVideoMapper.deleteVideoByChapterIds(chapterIds);
        return courseChapterMapper.deleteChapterByIds(chapterIds);
    }

    private void checkCourseExists(Long courseId)
    {
        if (StringUtils.isNull(courseId))
        {
            throw new ServiceException("章节必须属于某个课程");
        }
        Course course = courseMapper.selectCourseById(courseId);
        if (StringUtils.isNull(course))
        {
            throw new ServiceException("课程不存在或已被删除");
        }
    }

    private String trimChapterTitle(String chapterTitle)
    {
        String title = StringUtils.trim(chapterTitle);
        if (StringUtils.isEmpty(title))
        {
            throw new ServiceException("章节标题不能为空");
        }
        return title;
    }
}

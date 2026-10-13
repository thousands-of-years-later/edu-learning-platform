package com.ruoyi.course.service.impl;

import java.util.List;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.ruoyi.common.core.text.Convert;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.course.domain.Course;
import com.ruoyi.course.domain.CourseChapter;
import com.ruoyi.course.domain.CourseVideo;
import com.ruoyi.course.mapper.CourseChapterMapper;
import com.ruoyi.course.mapper.CourseMapper;
import com.ruoyi.course.mapper.CourseVideoMapper;
import com.ruoyi.course.service.ICourseVideoService;

/**
 * 课程视频 服务层处理
 *
 * @author edu
 */
@Service
public class CourseVideoServiceImpl implements ICourseVideoService
{
    @Autowired
    private CourseVideoMapper courseVideoMapper;

    @Autowired
    private CourseMapper courseMapper;

    @Autowired
    private CourseChapterMapper courseChapterMapper;

    @Override
    public List<CourseVideo> selectVideoList(CourseVideo video)
    {
        return courseVideoMapper.selectVideoList(video);
    }

    @Override
    public CourseVideo selectVideoById(Long videoId)
    {
        return courseVideoMapper.selectVideoById(videoId);
    }

    @Override
    public int insertVideo(CourseVideo video)
    {
        video.setVideoTitle(trimVideoTitle(video.getVideoTitle()));
        checkCourseAndChapter(video);
        return courseVideoMapper.insertVideo(video);
    }

    @Override
    public int updateVideo(CourseVideo video)
    {
        video.setVideoTitle(trimVideoTitle(video.getVideoTitle()));
        CourseVideo old = courseVideoMapper.selectVideoById(video.getVideoId());
        if (StringUtils.isNull(old))
        {
            throw new ServiceException("视频不存在或已被删除");
        }
        // 视频不允许搬家到别的课程
        video.setCourseId(old.getCourseId());
        checkCourseAndChapter(video);
        return courseVideoMapper.updateVideo(video);
    }

    @Override
    public int deleteVideoByIds(String ids)
    {
        Long[] videoIds = Convert.toLongArray(ids);
        // 空串会被 Convert 转成空数组，<foreach> 将拼出 in () 造成 SQL 语法错误，必须拦截
        if (videoIds == null || videoIds.length == 0)
        {
            throw new ServiceException("未选择要删除的视频数据");
        }
        for (Long videoId : videoIds)
        {
            CourseVideo video = courseVideoMapper.selectVideoById(videoId);
            if (StringUtils.isNull(video))
            {
                throw new ServiceException(String.format("视频ID[%s]不存在或已被删除", videoId));
            }
        }
        return courseVideoMapper.deleteVideoByIds(videoIds);
    }

    /**
     * 视频必须挂在存在的课程下；选了章节时，章节也必须存在且属于同一课程，
     * 否则会出现「A 课程的视频挂到 B 课程的章节下」这种跨课程脏数据。
     */
    private void checkCourseAndChapter(CourseVideo video)
    {
        if (StringUtils.isNull(video.getCourseId()))
        {
            throw new ServiceException("视频必须属于某个课程");
        }
        Course course = courseMapper.selectCourseById(video.getCourseId());
        if (StringUtils.isNull(course))
        {
            throw new ServiceException("课程不存在或已被删除");
        }
        if (StringUtils.isNotNull(video.getChapterId()))
        {
            CourseChapter chapter = courseChapterMapper.selectChapterById(video.getChapterId());
            if (StringUtils.isNull(chapter))
            {
                throw new ServiceException("章节不存在或已被删除");
            }
            if (!Objects.equals(chapter.getCourseId(), video.getCourseId()))
            {
                throw new ServiceException("所选章节不属于当前课程");
            }
        }
    }

    private String trimVideoTitle(String videoTitle)
    {
        String title = StringUtils.trim(videoTitle);
        if (StringUtils.isEmpty(title))
        {
            throw new ServiceException("视频标题不能为空");
        }
        return title;
    }
}

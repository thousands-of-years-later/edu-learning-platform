package com.ruoyi.course.service.impl;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ruoyi.common.constant.UserConstants;
import com.ruoyi.common.core.text.Convert;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.course.domain.Course;
import com.ruoyi.course.domain.CourseRating;
import com.ruoyi.course.mapper.CourseChapterMapper;
import com.ruoyi.course.mapper.CourseMapper;
import com.ruoyi.course.mapper.CourseVideoMapper;
import com.ruoyi.course.service.ICourseService;

/**
 * 课程信息 服务层处理
 *
 * @author edu
 */
@Service
public class CourseServiceImpl implements ICourseService
{
    @Autowired
    private CourseMapper courseMapper;

    @Autowired
    private CourseChapterMapper courseChapterMapper;

    @Autowired
    private CourseVideoMapper courseVideoMapper;

    @Override
    public List<Course> selectCourseList(Course course)
    {
        List<Course> list = courseMapper.selectCourseList(course);
        // 平均分统一在 Java 里算（规则见 CourseRating.avgScore，与评分接口同一套 HALF_UP）。
        // 只就地改元素、不新建 List —— list 就是 PageHelper 的 Page，
        // 换成新 List 会让 getDataTable 里的 PageInfo 拿不到 total。
        for (Course row : list)
        {
            long ratingCount = row.getRatingCount() == null ? 0L : row.getRatingCount().longValue();
            long ratingScore = row.getRatingScore() == null ? 0L : row.getRatingScore().longValue();
            row.setAvgScore(CourseRating.avgScore(ratingCount, ratingScore));
        }
        return list;
    }

    @Override
    public void checkSortBy(Course course)
    {
        String sortBy = StringUtils.trim(course.getSortBy());
        if (StringUtils.isEmpty(sortBy))
        {
            course.setSortBy(Course.SORT_BY_DEFAULT);
            return;
        }
        if (!Course.isValidSortBy(sortBy))
        {
            throw new ServiceException("排序方式不合法，只支持 likeCount、createTime、avgScore");
        }
        course.setSortBy(sortBy);
    }

    @Override
    public Course selectCourseById(Long courseId)
    {
        return courseMapper.selectCourseById(courseId);
    }

    @Override
    public boolean checkCourseNameUnique(Course course)
    {
        // 重名校验发生在 insert/update 之前，这里必须先归一化名称，
        // 否则 " java " 会因为带空白查不到同名记录而绕过校验
        course.setCourseName(StringUtils.trim(course.getCourseName()));
        Long courseId = StringUtils.isNull(course.getCourseId()) ? -1L : course.getCourseId();
        Course info = courseMapper.checkCourseNameUnique(course);
        if (StringUtils.isNotNull(info) && info.getCourseId().longValue() != courseId.longValue())
        {
            return UserConstants.NOT_UNIQUE;
        }
        return UserConstants.UNIQUE;
    }

    @Override
    public int insertCourse(Course course)
    {
        course.setCourseName(trimCourseName(course.getCourseName()));
        return courseMapper.insertCourse(course);
    }

    @Override
    public int updateCourse(Course course)
    {
        course.setCourseName(trimCourseName(course.getCourseName()));
        Course old = courseMapper.selectCourseById(course.getCourseId());
        if (StringUtils.isNull(old))
        {
            throw new ServiceException("课程不存在或已被删除");
        }
        return courseMapper.updateCourse(course);
    }

    /**
     * 删除课程时级联删掉它的章节和视频：三张表必须同进同退，
     * 否则会出现「课程没了、视频还在」的悬挂数据。
     */
    @Override
    @Transactional
    public int deleteCourseByIds(String ids)
    {
        Long[] courseIds = Convert.toLongArray(ids);
        // 空串会被 Convert 转成空数组，<foreach> 将拼出 in () 造成 SQL 语法错误，必须拦截
        if (courseIds == null || courseIds.length == 0)
        {
            throw new ServiceException("未选择要删除的课程数据");
        }
        for (Long courseId : courseIds)
        {
            Course course = courseMapper.selectCourseById(courseId);
            if (StringUtils.isNull(course))
            {
                throw new ServiceException(String.format("课程ID[%s]不存在或已被删除", courseId));
            }
        }
        courseVideoMapper.deleteVideoByCourseIds(courseIds);
        courseChapterMapper.deleteChapterByCourseIds(courseIds);
        return courseMapper.deleteCourseByIds(courseIds);
    }

    private String trimCourseName(String courseName)
    {
        String name = StringUtils.trim(courseName);
        if (StringUtils.isEmpty(name))
        {
            throw new ServiceException("课程名称不能为空");
        }
        return name;
    }
}

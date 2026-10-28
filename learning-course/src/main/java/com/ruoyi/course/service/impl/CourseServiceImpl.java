package com.ruoyi.course.service.impl;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ruoyi.common.constant.UserConstants;
import com.ruoyi.common.core.text.Convert;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.course.domain.Course;
import com.ruoyi.course.domain.CourseChapter;
import com.ruoyi.course.domain.CourseDetail;
import com.ruoyi.course.domain.CourseRating;
import com.ruoyi.course.domain.CourseVideo;
import com.ruoyi.course.mapper.CourseChapterMapper;
import com.ruoyi.course.mapper.CourseMapper;
import com.ruoyi.course.mapper.CourseRatingMapper;
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

    @Autowired
    private CourseRatingMapper courseRatingMapper;

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
    public CourseDetail selectCourseDetail(Long courseId)
    {
        Course course = courseMapper.selectCourseById(courseId);
        // selectCourseById 只排除已删除；停用课程对客户端同样不可见（与列表强制 status='0' 一致）
        if (StringUtils.isNull(course) || !UserConstants.NORMAL.equals(course.getStatus()))
        {
            return null;
        }
        // 平均分口径与列表一致，避免同一课程在列表和详情里显示不同的分数
        long ratingCount = course.getRatingCount() == null ? 0L : course.getRatingCount().longValue();
        long ratingScore = course.getRatingScore() == null ? 0L : course.getRatingScore().longValue();
        course.setAvgScore(CourseRating.avgScore(ratingCount, ratingScore));

        CourseChapter chapterQuery = new CourseChapter();
        chapterQuery.setCourseId(courseId);
        List<CourseChapter> chapters = courseChapterMapper.selectChapterList(chapterQuery);

        // chapterId -> 章节；顺便把每章的视频列表初始化成空数组，客户端不用处理 null
        Map<Long, CourseChapter> chapterMap = new LinkedHashMap<>();
        for (CourseChapter chapter : chapters)
        {
            chapter.setVideos(new ArrayList<>());
            chapterMap.put(chapter.getChapterId(), chapter);
        }

        CourseVideo videoQuery = new CourseVideo();
        videoQuery.setCourseId(courseId);
        List<CourseVideo> allVideos = courseVideoMapper.selectVideoList(videoQuery);

        // 挂章节的视频归到章节下；chapterId 为空（或章节已不存在）的视频留在课程级列表，不丢内容
        List<CourseVideo> courseVideos = new ArrayList<>();
        for (CourseVideo video : allVideos)
        {
            CourseChapter chapter = video.getChapterId() == null ? null : chapterMap.get(video.getChapterId());
            if (chapter == null)
            {
                courseVideos.add(video);
            }
            else
            {
                chapter.getVideos().add(video);
            }
        }

        CourseDetail detail = new CourseDetail();
        detail.setCourse(course);
        detail.setChapters(chapters);
        detail.setVideos(courseVideos);
        return detail;
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
     * 删除课程时级联清理四张子表：点赞收藏、评论、课程视频、章节。
     * 五张表必须同进同退，否则会出现「课程没了、视频/评论/互动还在」的悬挂数据。
     *
     * 顺序：先删课程本身（软删 + 清零计数列），再清互动与评论，最后删课程视频与章节。
     * 之所以把课程放到最前，是为了抢在并发点赞/评论之前 —— 它们的 existsCourse 与
     * updateCount 都带 del_flag='0'，课程先落删除态，并发的子表写入要么被拦住、
     * 要么影响 0 行而回滚；反过来先清子表的话，中间窗口里提交的互动会永远留在库里。
     * 原子性由 @Transactional 保证，不靠语句顺序。
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
        // 1、软删课程并清零计数（点赞/收藏/评论 + 评分两列，同一句 SQL）
        int rows = courseMapper.deleteCourseByIds(courseIds);
        // 2、清掉挂在课程上的点赞收藏（硬删除）与评论（软删除）
        courseMapper.deleteInteractionByCourseIds(courseIds);
        courseMapper.softDeleteCommentByCourseIds(courseIds);
        // 3、清掉挂在课程上的评分（硬删除）：评分表没有 del_flag，课程删了评分就没有意义，
        //    留着会成为孤儿数据；同一事务里删干净，课程行的评分冗余列也在第 1 步一并清零
        courseRatingMapper.deleteRatingByCourseIds(courseIds);
        // 4、原有的课程内级联：课程视频与章节
        courseVideoMapper.deleteVideoByCourseIds(courseIds);
        courseChapterMapper.deleteChapterByCourseIds(courseIds);
        return rows;
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

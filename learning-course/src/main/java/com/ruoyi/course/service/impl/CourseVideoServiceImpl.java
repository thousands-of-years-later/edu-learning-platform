package com.ruoyi.course.service.impl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
import com.ruoyi.course.utils.VideoDurationUtils;

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
     * 分批重新探测视频时长：同一批内同一 URL 只下载/解析一次，只更新有变化的记录。
     * 分页参数由控制层传入，避免一次请求跑全量被网关超时截断。
     */
    @Override
    public Map<String, Object> refreshDurations(String operator, int offset, int batch)
    {
        List<CourseVideo> videos = courseVideoMapper.selectVideoList(new CourseVideo());
        int total = videos.size();
        int start = Math.max(0, offset);
        int end = Math.min(total, start + Math.max(1, batch));
        Map<String, Integer> cache = new HashMap<>();
        List<Map<String, Object>> failures = new ArrayList<>();
        int scanned = 0;
        int updated = 0;
        int failed = 0;
        int skipped = 0;
        for (int i = start; i < end; i++)
        {
            CourseVideo video = videos.get(i);
            scanned++;
            String url = video.getVideoUrl();
            if (StringUtils.isEmpty(url) || !url.startsWith("http"))
            {
                // 本地相对路径和历史脏数据无法远程探测，单独计数，不混进失败
                skipped++;
                continue;
            }
            Integer detected;
            if (cache.containsKey(url))
            {
                detected = cache.get(url);
            }
            else
            {
                detected = VideoDurationUtils.fromUrl(url);
                cache.put(url, detected);
            }
            if (detected == null)
            {
                failed++;
                if (failures.size() < 10)
                {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("videoId", video.getVideoId());
                    item.put("videoUrl", url);
                    failures.add(item);
                }
                continue;
            }
            if (detected.equals(video.getDuration()))
            {
                continue;
            }
            CourseVideo update = new CourseVideo();
            update.setVideoId(video.getVideoId());
            update.setDuration(detected);
            update.setUpdateBy(operator);
            updated += courseVideoMapper.updateVideo(update);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", total);
        result.put("offset", start);
        result.put("scanned", scanned);
        result.put("updated", updated);
        result.put("failed", failed);
        result.put("skipped", skipped);
        result.put("nextOffset", end);
        result.put("done", end >= total);
        result.put("failures", failures);
        return result;
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

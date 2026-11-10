package com.ruoyi.course.controller;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.course.domain.Course;
import com.ruoyi.course.domain.CourseChapter;
import com.ruoyi.course.domain.CourseVideo;
import com.ruoyi.course.service.ICourseChapterService;
import com.ruoyi.course.service.ICourseService;
import com.ruoyi.course.service.ICourseVideoService;

/**
 * 课程目录（后台树形结构）
 *
 * 替换原来「章节」「视频」两个分散的页签：一页看完整结构
 *   课程 → 章节 → 小节视频
 * 点击课程展开它有的章节，点击章节展开章节里的小节（bootstrap 表格树，和前端目录页一致）。
 *
 * 节点 id 规则（表格树靠 code/parentCode 组父子关系）：
 *   课程  c + courseId  （parentId = "0"）
 *   章节  h + chapterId （parentId = c + courseId）
 *   小节  v + videoId   （parentId = h + chapterId；没有章节的小节直接挂课程下）
 *
 * 增删改复用已有页面/接口：/course/chapter/add/{courseId}、/course/video/add/{courseId}?chapterId=
 * 以及 /course/chapter/remove、/course/video/remove，权限沿用 course:course:*。
 *
 * @author edu
 */
@Controller
@RequestMapping("/course/catalog")
public class CourseCatalogController extends BaseController
{
    @Autowired
    private ICourseService courseService;

    @Autowired
    private ICourseChapterService courseChapterService;

    @Autowired
    private ICourseVideoService courseVideoService;

    /**
     * 课程目录页
     *
     * @param courseId 课程ID（从课程管理行内进入时带上；不传则展示全部课程）
     */
    @RequiresPermissions("course:course:view")
    @GetMapping()
    public String catalog(Long courseId, ModelMap mmap)
    {
        mmap.put("courseId", courseId);
        return "course/catalog/catalog";
    }

    /**
     * 目录数据（表格树接口，不分页，返回数组）
     *
     * @param courseId 课程ID（可空：空则返回全部课程）
     */
    @RequiresPermissions("course:course:view")
    @PostMapping("/list")
    @ResponseBody
    public List<Map<String, Object>> list(Long courseId)
    {
        List<Map<String, Object>> rows = new ArrayList<>();

        // 1、课程节点
        List<Course> courses = courseService.selectCourseList(new Course());
        List<Course> targets = new ArrayList<>();
        for (Course course : courses)
        {
            if (courseId == null || courseId.equals(course.getCourseId()))
            {
                targets.add(course);
            }
        }

        for (Course course : targets)
        {
            Map<String, Object> node = newNode("c" + course.getCourseId(), "0", "course",
                    course.getCourseName(), course.getCourseId(), null, null);
            node.put("courseName", course.getCourseName());
            node.put("status", course.getStatus());
            node.put("coverUrl", course.getCoverUrl());
            node.put("intro", course.getIntro());
            node.put("commentCount", course.getCommentCount());
            node.put("likeCount", course.getLikeCount());
            rows.add(node);

            // 2、章节节点
            int chapterStart = rows.size();
            CourseChapter chapterQuery = new CourseChapter();
            chapterQuery.setCourseId(course.getCourseId());
            List<CourseChapter> chapters = courseChapterService.selectChapterList(chapterQuery);
            for (CourseChapter chapter : chapters)
            {
                Map<String, Object> chapterNode = newNode("h" + chapter.getChapterId(), "c" + course.getCourseId(),
                        "chapter", chapter.getChapterTitle(), course.getCourseId(), chapter.getChapterId(), null);
                chapterNode.put("courseName", course.getCourseName());
                chapterNode.put("orderNum", chapter.getOrderNum());
                chapterNode.put("chapterContent", chapter.getChapterContent());
                rows.add(chapterNode);
            }

            // 3、小节节点（按章节挂；没有章节的挂课程下）
            CourseVideo videoQuery = new CourseVideo();
            videoQuery.setCourseId(course.getCourseId());
            List<CourseVideo> videos = courseVideoService.selectVideoList(videoQuery);
            for (CourseVideo video : videos)
            {
                String parent = video.getChapterId() == null ? "c" + course.getCourseId()
                        : "h" + video.getChapterId();
                Map<String, Object> videoNode = newNode("v" + video.getVideoId(), parent, "video",
                        video.getVideoTitle(), course.getCourseId(), video.getChapterId(), video.getVideoId());
                videoNode.put("courseName", course.getCourseName());
                videoNode.put("orderNum", video.getOrderNum());
                videoNode.put("duration", video.getDuration());
                videoNode.put("videoUrl", video.getVideoUrl());
                videoNode.put("chapterTitle", video.getChapterTitle());
                videoNode.put("createTime", video.getCreateTime());
                videoNode.put("updateTime", video.getUpdateTime());
                rows.add(videoNode);
            }

            // 4、给章节补"小节数量 / 第一个小节"，章节行上的播放和详情直接用
            for (int i = chapterStart; i < rows.size(); i++)
            {
                Map<String, Object> chapterNode = rows.get(i);
                if (!"chapter".equals(chapterNode.get("type")))
                {
                    continue;
                }
                int videoCount = 0;
                Map<String, Object> firstVideo = null;
                for (Map<String, Object> videoNode : rows)
                {
                    if ("video".equals(videoNode.get("type"))
                            && chapterNode.get("id").equals(videoNode.get("parentId")))
                    {
                        videoCount++;
                        if (firstVideo == null)
                        {
                            firstVideo = videoNode;
                        }
                    }
                }
                chapterNode.put("videoCount", videoCount);
                if (firstVideo != null)
                {
                    chapterNode.put("firstVideoUrl", firstVideo.get("videoUrl"));
                    chapterNode.put("firstVideoTitle", firstVideo.get("name"));
                }
            }
        }
        return rows;
    }

    /**
     * 某个章节下的小节列表（章节行的播放/详情用）
     *
     * @param chapterId 章节ID
     */
    @RequiresPermissions("course:course:view")
    @PostMapping("/videos")
    @ResponseBody
    public List<CourseVideo> videos(Long chapterId)
    {
        if (chapterId == null)
        {
            return new ArrayList<>();
        }
        CourseVideo query = new CourseVideo();
        query.setChapterId(chapterId);
        return courseVideoService.selectVideoList(query);
    }

    /**
     * 统一构造一个树节点
     */
    private Map<String, Object> newNode(String id, String parentId, String type, String name,
                                        Long courseId, Long chapterId, Long videoId)
    {
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("id", id);
        node.put("parentId", parentId);
        node.put("type", type);
        node.put("name", name);
        node.put("courseId", courseId);
        node.put("chapterId", chapterId);
        node.put("videoId", videoId);
        return node;
    }
}

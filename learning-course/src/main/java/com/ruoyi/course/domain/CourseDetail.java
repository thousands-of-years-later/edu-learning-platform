package com.ruoyi.course.domain;

import java.io.Serializable;
import java.util.List;

/**
 * 课程详情（客户端）
 *
 * 由三段组成：
 *   course   课程本体，含简介（intro）、封面（coverUrl）等列表已有字段；
 *   chapters 章节，按 order_num 升序，每个章节的 videos 字段是该章节下的课程视频；
 *   videos   没有挂章节、直接属于课程的视频（chapter_id 为空），按 order_num 升序。
 *
 * 这里的视频只来自 learning_course_video（课程内的课时视频），
 * 与短视频模块 learning_short_video 无关。
 *
 * @author edu
 */
public class CourseDetail implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 课程本体 */
    private Course course;

    /** 章节列表（每章带自己的视频） */
    private List<CourseChapter> chapters;

    /** 无章节的课程视频 */
    private List<CourseVideo> videos;

    public Course getCourse()
    {
        return course;
    }

    public void setCourse(Course course)
    {
        this.course = course;
    }

    public List<CourseChapter> getChapters()
    {
        return chapters;
    }

    public void setChapters(List<CourseChapter> chapters)
    {
        this.chapters = chapters;
    }

    public List<CourseVideo> getVideos()
    {
        return videos;
    }

    public void setVideos(List<CourseVideo> videos)
    {
        this.videos = videos;
    }
}

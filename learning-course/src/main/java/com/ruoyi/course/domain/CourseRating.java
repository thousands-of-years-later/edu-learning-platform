package com.ruoyi.course.domain;

import java.io.Serializable;
import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;

/**
 * 课程评分表 learning_course_rating
 *
 * 评分【只针对课程】（不做点赞/评论那种 target_type 多态）：每个用户对每门课程
 * 最多一条评分记录（唯一索引 uk_course_user 保证），改分是 update，不是插新行。
 * 本表没有 del_flag：评分没有删除语义，唯一索引因此是安全的。
 *
 * 计数字段（learning_course.rating_count / rating_score）只由评分模块维护，
 * 后台编辑课程时不再改动它们；接口返回的平均分与评分人数以评分表的
 * count(*) / sum(score) 为唯一真相，不读那两列。
 *
 * 故意不继承 BaseEntity：本表没有 create_by / update_by / remark，
 * 继承了反而会让 getParams() 往每个返回行里多塞一个空的 params。
 *
 * @author edu
 */
public class CourseRating implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 允许的最低分 */
    public static final int MIN_SCORE = 1;

    /** 允许的最高分 */
    public static final int MAX_SCORE = 5;

    private Long ratingId;

    /** 课程ID（learning_course.course_id） */
    private Long courseId;

    /** 评分人用户ID（sys_user.user_id） */
    private Long userId;

    /** 评分（1-5 的整数） */
    private Integer score;

    /** 首次评分时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date createTime;

    /** 最近一次修改时间（没改过为 null） */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date updateTime;

    public Long getRatingId()
    {
        return ratingId;
    }

    public void setRatingId(Long ratingId)
    {
        this.ratingId = ratingId;
    }

    public Long getCourseId()
    {
        return courseId;
    }

    public void setCourseId(Long courseId)
    {
        this.courseId = courseId;
    }

    public Long getUserId()
    {
        return userId;
    }

    public void setUserId(Long userId)
    {
        this.userId = userId;
    }

    public Integer getScore()
    {
        return score;
    }

    public void setScore(Integer score)
    {
        this.score = score;
    }

    public Date getCreateTime()
    {
        return createTime;
    }

    public void setCreateTime(Date createTime)
    {
        this.createTime = createTime;
    }

    public Date getUpdateTime()
    {
        return updateTime;
    }

    public void setUpdateTime(Date updateTime)
    {
        this.updateTime = updateTime;
    }

    /** 分数是否合法（1-5 的整数；null / 越界均不合法） */
    public static boolean isValidScore(Integer score)
    {
        return score != null && score >= MIN_SCORE && score <= MAX_SCORE;
    }
}

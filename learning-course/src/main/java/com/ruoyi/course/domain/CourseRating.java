package com.ruoyi.course.domain;

import java.io.Serializable;
import java.math.BigDecimal;
import java.math.RoundingMode;
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

    /** 文字评价最大长度（与 learning_course_rating.content 的 varchar(500) 对齐） */
    public static final int MAX_CONTENT_LENGTH = 500;

    private Long ratingId;

    /** 课程ID（learning_course.course_id） */
    private Long courseId;

    /** 评分人用户ID（sys_user.user_id） */
    private Long userId;

    /** 评分（1-5 的整数） */
    private Integer score;

    /** 文字评价（可空：只打星不写字是允许的） */
    private String content;

    /** 首次评分时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date createTime;

    /** 最近一次修改时间（没改过为 null） */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date updateTime;

    // ===== 以下是展示字段：表里没有对应列，评价列表由 Mapper 的子查询填充 =====

    /** 评分人昵称 */
    private String userName;

    /** 评分人头像（可能为 null，前端用昵称首字兜底） */
    private String avatar;

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

    public String getContent()
    {
        return content;
    }

    public void setContent(String content)
    {
        this.content = content;
    }

    public String getUserName()
    {
        return userName;
    }

    public void setUserName(String userName)
    {
        this.userName = userName;
    }

    public String getAvatar()
    {
        return avatar;
    }

    public void setAvatar(String avatar)
    {
        this.avatar = avatar;
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

    /**
     * 课程平均分（保留 1 位小数，HALF_UP；无人评分返回 null，绝不返回假 0 分）
     *
     * 评分接口（/api/rating/info 的 avgScore）与课程列表接口共用这一条规则，
     * 避免两处实现漂移：列表里的 avgScore 与排序所用的 avgScore 必须是同一个值。
     * 用 BigDecimal 除法而不是 double：4.35 这类值用 double 会变成 4.3499… 被舍成 4.3。
     *
     * @param ratingCount 评分人数
     * @param ratingScore 评分总分（各用户评分之和）
     */
    public static Double avgScore(long ratingCount, long ratingScore)
    {
        if (ratingCount <= 0)
        {
            return null;
        }
        return BigDecimal.valueOf(ratingScore)
                .divide(BigDecimal.valueOf(ratingCount), 1, RoundingMode.HALF_UP)
                .doubleValue();
    }
}

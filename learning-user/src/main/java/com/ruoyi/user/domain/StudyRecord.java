package com.ruoyi.user.domain;

import java.io.Serializable;
import java.time.LocalDate;

/**
 * 学习时长记录 learning_study_record（按天一行）
 *
 * 只用于"本周柱状图 / 连续学习天数"的计算，不直接返回给客户端：
 * 客户端拿到的是 {@link StudyDay}（日期 + 星期 + 分钟）。
 *
 * @author edu
 */
public class StudyRecord implements Serializable
{
    private static final long serialVersionUID = 1L;

    private Long recordId;

    private Long userId;

    /** 学习日期（Asia/Shanghai） */
    private LocalDate studyDate;

    /** 当天累计学习秒数 */
    private Integer seconds;

    public Long getRecordId()
    {
        return recordId;
    }

    public void setRecordId(Long recordId)
    {
        this.recordId = recordId;
    }

    public Long getUserId()
    {
        return userId;
    }

    public void setUserId(Long userId)
    {
        this.userId = userId;
    }

    public LocalDate getStudyDate()
    {
        return studyDate;
    }

    public void setStudyDate(LocalDate studyDate)
    {
        this.studyDate = studyDate;
    }

    public Integer getSeconds()
    {
        return seconds;
    }

    public void setSeconds(Integer seconds)
    {
        this.seconds = seconds;
    }
}

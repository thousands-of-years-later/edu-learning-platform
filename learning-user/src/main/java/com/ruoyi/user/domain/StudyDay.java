package com.ruoyi.user.domain;

import java.io.Serializable;

/**
 * 学习数据的一天（设计稿 20_学习数据 的"本周学习时长"柱状图）
 *
 * 只有 7 条：本周一到周日；没有学习记录的那天 minutes=0（前端画空柱子）。
 *
 * @author edu
 */
public class StudyDay implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 日期，如 2026-11-02 */
    private String date;

    /** 星期中文，如 一 / 二 … 日（设计稿柱状图下面的标签） */
    private String weekday;

    /** 当天学习分钟数（秒数四舍五入到分钟） */
    private Integer minutes;

    public StudyDay()
    {
    }

    public StudyDay(String date, String weekday, Integer minutes)
    {
        this.date = date;
        this.weekday = weekday;
        this.minutes = minutes;
    }

    public String getDate()
    {
        return date;
    }

    public void setDate(String date)
    {
        this.date = date;
    }

    public String getWeekday()
    {
        return weekday;
    }

    public void setWeekday(String weekday)
    {
        this.weekday = weekday;
    }

    public Integer getMinutes()
    {
        return minutes;
    }

    public void setMinutes(Integer minutes)
    {
        this.minutes = minutes;
    }
}

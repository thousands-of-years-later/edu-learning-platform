package com.ruoyi.user.domain;

import java.io.Serializable;
import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;

/**
 * 签到记录 learning_checkin
 *
 * 一天一条：唯一索引 (user_id, sign_date) 兜底，并发双击也只有一次插入能成功。
 * 用 DATE 而不是 datetime 存日期 —— 按「自然日」判重，绕开 00:00 边界与时区换算；
 * 「今天」统一由服务端按东八区取（见 CheckinServiceImpl.today()）。
 *
 * signDate 特意用 String（yyyy-MM-dd）而不是 Date：
 *   1) 接口直接返回 "2026-10-09"，前端不用再格式化；
 *   2) 日期只做「相等 / 范围」比较，不做时间运算，String 最不容易踩时区坑。
 *
 * continuousDays 记录【这次签到之后】的连续天数，于是：
 *   今天连续天数 = 昨天有记录 ? 昨天那行的 continuousDays + 1 : 1
 * 不需要遍历历史，也不用给断掉的日子补行。
 *
 * points 存【当次得分】：设计稿「最近签到」要显示每条的 +N，
 * 所以档位以后调整也不会改写历史记录（档位表见 {@link CheckinRule}）。
 *
 * 故意不继承 BaseEntity：本表没有 create_by / update_by / remark。
 *
 * @author edu
 */
public class CheckinRecord implements Serializable
{
    private static final long serialVersionUID = 1L;

    private Long checkinId;

    /** 用户ID（sys_user.user_id） */
    private Long userId;

    /** 签到日期 yyyy-MM-dd（东八区自然日） */
    private String signDate;

    /** 本次获得的积分 */
    private Integer points;

    /** 本次签到后的连续签到天数 */
    private Integer continuousDays;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date createTime;

    public Long getCheckinId()
    {
        return checkinId;
    }

    public void setCheckinId(Long checkinId)
    {
        this.checkinId = checkinId;
    }

    public Long getUserId()
    {
        return userId;
    }

    public void setUserId(Long userId)
    {
        this.userId = userId;
    }

    public String getSignDate()
    {
        return signDate;
    }

    public void setSignDate(String signDate)
    {
        this.signDate = signDate;
    }

    public Integer getPoints()
    {
        return points;
    }

    public void setPoints(Integer points)
    {
        this.points = points;
    }

    public Integer getContinuousDays()
    {
        return continuousDays;
    }

    public void setContinuousDays(Integer continuousDays)
    {
        this.continuousDays = continuousDays;
    }

    public Date getCreateTime()
    {
        return createTime;
    }

    public void setCreateTime(Date createTime)
    {
        this.createTime = createTime;
    }
}

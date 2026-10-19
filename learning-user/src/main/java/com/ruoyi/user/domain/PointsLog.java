package com.ruoyi.user.domain;

import java.io.Serializable;
import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;

/**
 * 积分流水 learning_points_log
 *
 * 只增不改：每一次余额变动都留一条（签到 +N / 兑换 -N / 手工调整），
 * 用于对账和排查「我的积分怎么少了」。
 * balance 存【变动后的余额快照】：任意一条流水都能看出当时余额，不必重放全表。
 *
 * type 目前只有 checkin（本模块），兑换模块以后写 exchange，后台手工调整写 adjust。
 *
 * @author edu
 */
public class PointsLog implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 流水类型：签到 */
    public static final String TYPE_CHECKIN = "checkin";

    /** 流水类型：兑换（免广告板块使用，本模块只预留） */
    public static final String TYPE_EXCHANGE = "exchange";

    /** 流水类型：手工调整（后台使用，本模块只预留） */
    public static final String TYPE_ADJUST = "adjust";

    private Long logId;

    /** 用户ID（sys_user.user_id） */
    private Long userId;

    /** 变动值（签到为正、兑换为负） */
    private Integer changeAmount;

    /** 变动后的余额快照 */
    private Integer balance;

    /** 类型：checkin / exchange / adjust */
    private String type;

    /** 关联业务ID（签到记录 checkin_id 等） */
    private Long refId;

    private String remark;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date createTime;

    public Long getLogId()
    {
        return logId;
    }

    public void setLogId(Long logId)
    {
        this.logId = logId;
    }

    public Long getUserId()
    {
        return userId;
    }

    public void setUserId(Long userId)
    {
        this.userId = userId;
    }

    public Integer getChangeAmount()
    {
        return changeAmount;
    }

    public void setChangeAmount(Integer changeAmount)
    {
        this.changeAmount = changeAmount;
    }

    public Integer getBalance()
    {
        return balance;
    }

    public void setBalance(Integer balance)
    {
        this.balance = balance;
    }

    public String getType()
    {
        return type;
    }

    public void setType(String type)
    {
        this.type = type;
    }

    public Long getRefId()
    {
        return refId;
    }

    public void setRefId(Long refId)
    {
        this.refId = refId;
    }

    public String getRemark()
    {
        return remark;
    }

    public void setRemark(String remark)
    {
        this.remark = remark;
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

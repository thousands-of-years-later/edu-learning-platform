package com.ruoyi.user.domain;

import java.io.Serializable;
import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;

/**
 * 积分账户 learning_points_account
 *
 * 一个用户一行（user_id 直接做主键）。balance 就是设计稿里的「我的积分 / 总积分」——
 * 【余额】语义：签到 +N，以后兑换免广告 -N，花掉就没了。
 * totalEarned 是历史累计获得（只增），只备查，不参与任何展示口径。
 *
 * 免广告权益【不在本表】：按约定它属于另一个板块，本模块只维护余额；
 * 兑换模块以后从同一个 balance 里扣分，并且必须同时写一条 learning_points_log。
 *
 * 行是【懒创建】的：老用户第一次签到 / 查账户时才插入，余额从 0 开始，
 * 这样不用给历史用户批量补数据，也不会出现「账户不存在」的空指针。
 *
 * @author edu
 */
public class PointsAccount implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 用户ID（sys_user.user_id，主键） */
    private Long userId;

    /** 积分余额（页面上的"我的积分 / 总积分"） */
    private Integer balance;

    /** 历史累计获得（只增，备查） */
    private Integer totalEarned;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date updateTime;

    public Long getUserId()
    {
        return userId;
    }

    public void setUserId(Long userId)
    {
        this.userId = userId;
    }

    public Integer getBalance()
    {
        return balance;
    }

    public void setBalance(Integer balance)
    {
        this.balance = balance;
    }

    public Integer getTotalEarned()
    {
        return totalEarned;
    }

    public void setTotalEarned(Integer totalEarned)
    {
        this.totalEarned = totalEarned;
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
}

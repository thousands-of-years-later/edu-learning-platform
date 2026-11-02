package com.ruoyi.community.domain;

import java.io.Serializable;
import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;

/**
 * 话题 learning_topic（设计稿 17_话题页 / 16_文章详情的话题标签）
 *
 * 话题由管理员创建，用户发帖时从已有话题里选，不允许自由输入
 * （否则会出现 Android / 安卓 / android 这种同义话题把内容拆散）。
 *
 * postCount 不是表字段，是查询时用关联表实时 count 出来的。
 *
 * @author edu
 */
public class Topic implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 一篇文章最多能选几个话题 */
    public static final int MAX_TOPICS_PER_POST = 5;

    /** 话题名最大长度 */
    public static final int MAX_NAME_LENGTH = 50;

    private Long topicId;

    /** 话题名（不含 # 号，如 Android） */
    private String topicName;

    /** 话题简介（话题页副标题） */
    private String topicDesc;

    /** 状态（0正常 1停用） */
    private String status;

    /** 删除标志（0存在 2删除） */
    private String delFlag;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date createTime;

    // ===== 展示字段：表里没有，查询时算出来 =====

    /** 该话题下已发布文章数 */
    private Integer postCount;

    public Long getTopicId()
    {
        return topicId;
    }

    public void setTopicId(Long topicId)
    {
        this.topicId = topicId;
    }

    public String getTopicName()
    {
        return topicName;
    }

    public void setTopicName(String topicName)
    {
        this.topicName = topicName;
    }

    public String getTopicDesc()
    {
        return topicDesc;
    }

    public void setTopicDesc(String topicDesc)
    {
        this.topicDesc = topicDesc;
    }

    public String getStatus()
    {
        return status;
    }

    public void setStatus(String status)
    {
        this.status = status;
    }

    public String getDelFlag()
    {
        return delFlag;
    }

    public void setDelFlag(String delFlag)
    {
        this.delFlag = delFlag;
    }

    public Date getCreateTime()
    {
        return createTime;
    }

    public void setCreateTime(Date createTime)
    {
        this.createTime = createTime;
    }

    public Integer getPostCount()
    {
        return postCount;
    }

    public void setPostCount(Integer postCount)
    {
        this.postCount = postCount;
    }
}

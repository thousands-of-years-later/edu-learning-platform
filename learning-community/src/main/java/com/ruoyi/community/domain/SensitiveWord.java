package com.ruoyi.community.domain;

import com.ruoyi.common.core.domain.BaseEntity;

/**
 * 违禁词 learning_sensitive_word
 *
 * 只用于帖子标题/正文的发布校验；评论不处理。
 *
 * @author edu
 */
public class SensitiveWord extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 主键 */
    private Long wordId;

    /** 违禁词 */
    private String word;

    /** 状态（0正常 1停用） */
    private String status;

    /** 备注 */
    private String remark;

    public Long getWordId()
    {
        return wordId;
    }

    public void setWordId(Long wordId)
    {
        this.wordId = wordId;
    }

    public String getWord()
    {
        return word;
    }

    public void setWord(String word)
    {
        this.word = word;
    }

    public String getStatus()
    {
        return status;
    }

    public void setStatus(String status)
    {
        this.status = status;
    }

    @Override
    public String getRemark()
    {
        return remark;
    }

    @Override
    public void setRemark(String remark)
    {
        this.remark = remark;
    }
}

package com.ruoyi.course.domain;

import java.io.Serializable;

/**
 * 客户端类别列表项（课程 / 社区文章的分类筛选标签）
 *
 * 只保留客户端渲染分类标签需要的字段：类别ID、类别名称。
 * status / delFlag / orderNum / createBy 属于后台管理字段，不出现在客户端响应里；
 * 排序已由 CategoryMapper.xml 按 order_num、category_id 完成，客户端按返回顺序渲染即可。
 *
 * @author edu
 */
public class CategorySummary implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 类别ID */
    private Long categoryId;

    /** 类别名称 */
    private String categoryName;

    public Long getCategoryId()
    {
        return categoryId;
    }

    public void setCategoryId(Long categoryId)
    {
        this.categoryId = categoryId;
    }

    public String getCategoryName()
    {
        return categoryName;
    }

    public void setCategoryName(String categoryName)
    {
        this.categoryName = categoryName;
    }
}

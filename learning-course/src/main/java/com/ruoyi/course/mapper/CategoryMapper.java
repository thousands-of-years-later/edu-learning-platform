package com.ruoyi.course.mapper;

import java.util.List;
import com.ruoyi.course.domain.Category;

/**
 * 类别信息 数据层
 *
 * @author edu
 */
public interface CategoryMapper
{
    /**
     * 查询类别列表
     *
     * @param category 类别信息（categoryType 必传，用于按模块隔离数据）
     * @return 类别集合
     */
    public List<Category> selectCategoryList(Category category);

    /**
     * 根据类别ID查询类别（只查未删除的）
     *
     * @param categoryId 类别ID
     * @return 类别信息
     */
    public Category selectCategoryById(Long categoryId);

    /**
     * 校验同一类别类型下名称是否唯一（只查未删除的）
     *
     * @param category 类别信息（categoryName、categoryType 必传）
     * @return 类别信息
     */
    public Category checkCategoryNameUnique(Category category);

    /**
     * 新增类别
     *
     * @param category 类别信息
     * @return 结果
     */
    public int insertCategory(Category category);

    /**
     * 修改类别
     *
     * @param category 类别信息
     * @return 结果
     */
    public int updateCategory(Category category);

    /**
     * 批量逻辑删除类别
     *
     * @param categoryIds 需要删除的类别ID
     * @return 结果
     */
    public int deleteCategoryByIds(Long[] categoryIds);

    /**
     * 统计某社区类别下未删除的帖子数量（含草稿与已下架）
     *
     * 这是刻意的跨模块 SQL：帖子实体在 learning-community，而本模块（learning-course）
     * 是 community 的依赖方，反向注入 IPostService 会形成循环依赖。
     * 这里只写一条纯 SQL 统计 learning_post，没有编译期依赖。
     * 注意：需要先执行过 sql/learning_post.sql，表不存在时本查询会报表不存在。
     *
     * @param categoryId 类别ID
     * @return 帖子数量
     */
    public int countPostByCategoryId(Long categoryId);
}

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
}

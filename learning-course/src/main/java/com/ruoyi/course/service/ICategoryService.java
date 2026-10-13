package com.ruoyi.course.service;

import java.util.List;
import com.ruoyi.course.domain.Category;

/**
 * 类别信息 服务层
 *
 * @author edu
 */
public interface ICategoryService
{
    /**
     * 查询类别列表
     *
     * @param category 类别信息（categoryType 由控制层强制赋值，用于按模块隔离数据）
     * @return 类别集合
     */
    public List<Category> selectCategoryList(Category category);

    /**
     * 根据类别ID查询类别
     *
     * @param categoryId 类别ID
     * @return 类别信息
     */
    public Category selectCategoryById(Long categoryId);

    /**
     * 校验同一类别类型下类别名称是否唯一
     *
     * @param category 类别信息
     * @return 结果
     */
    public boolean checkCategoryNameUnique(Category category);

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
     * @param ids 需要删除的数据ID
     * @param categoryType 当前模块的类别类型，用于防止跨模块删除
     * @return 结果
     */
    public int deleteCategoryByIds(String ids, String categoryType);

    /**
     * 统计该类别下已关联的内容数量（课程/文章）
     *
     * course 类型按 learning_course 统计，article 类型按 learning_post 统计
     * （含草稿与已下架，避免删类别时把草稿带走）。
     *
     * @param categoryId 类别ID
     * @param categoryType 类别类型
     * @return 关联内容数量
     */
    public int countContentByCategoryId(Long categoryId, String categoryType);
}

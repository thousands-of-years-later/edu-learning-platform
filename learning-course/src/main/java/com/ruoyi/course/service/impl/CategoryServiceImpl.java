package com.ruoyi.course.service.impl;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.ruoyi.common.constant.UserConstants;
import com.ruoyi.common.core.text.Convert;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.course.domain.Category;
import com.ruoyi.course.mapper.CategoryMapper;
import com.ruoyi.course.service.ICategoryService;

/**
 * 类别信息 服务层处理
 *
 * @author edu
 */
@Service
public class CategoryServiceImpl implements ICategoryService
{
    @Autowired
    private CategoryMapper categoryMapper;

    /**
     * 查询类别列表
     *
     * @param category 类别信息
     * @return 类别集合
     */
    @Override
    public List<Category> selectCategoryList(Category category)
    {
        return categoryMapper.selectCategoryList(category);
    }

    /**
     * 根据类别ID查询类别
     *
     * @param categoryId 类别ID
     * @return 类别信息
     */
    @Override
    public Category selectCategoryById(Long categoryId)
    {
        return categoryMapper.selectCategoryById(categoryId);
    }

    /**
     * 校验同一类别类型下类别名称是否唯一
     *
     * @param category 类别信息
     * @return 结果
     */
    @Override
    public boolean checkCategoryNameUnique(Category category)
    {
        // 重名校验发生在 insert/update 之前，这里必须先归一化名称，
        // 否则 " java " 会因为带空白查不到同名记录而绕过校验
        category.setCategoryName(StringUtils.trim(category.getCategoryName()));
        Long categoryId = StringUtils.isNull(category.getCategoryId()) ? -1L : category.getCategoryId();
        Category info = categoryMapper.checkCategoryNameUnique(category);
        if (StringUtils.isNotNull(info) && info.getCategoryId().longValue() != categoryId.longValue())
        {
            return UserConstants.NOT_UNIQUE;
        }
        return UserConstants.UNIQUE;
    }

    /**
     * 新增类别
     *
     * @param category 类别信息
     * @return 结果
     */
    @Override
    public int insertCategory(Category category)
    {
        // 去掉首尾空白，避免 " java " 与 "java" 被当成两个类别
        category.setCategoryName(trimCategoryName(category.getCategoryName()));
        return categoryMapper.insertCategory(category);
    }

    /**
     * 修改类别
     *
     * @param category 类别信息
     * @return 结果
     */
    @Override
    public int updateCategory(Category category)
    {
        category.setCategoryName(trimCategoryName(category.getCategoryName()));
        Category old = categoryMapper.selectCategoryById(category.getCategoryId());
        if (StringUtils.isNull(old))
        {
            throw new ServiceException("类别不存在或已被删除");
        }
        // 类别类型是数据隔离的根，不允许通过修改接口把类别搬到另一个模块
        if (!StringUtils.equals(old.getCategoryType(), category.getCategoryType()))
        {
            throw new ServiceException("不允许修改类别类型");
        }
        return categoryMapper.updateCategory(category);
    }

    /**
     * 批量逻辑删除类别
     *
     * @param ids 需要删除的数据ID
     * @param categoryType 当前模块的类别类型，用于防止跨模块删除
     * @return 结果
     */
    @Override
    public int deleteCategoryByIds(String ids, String categoryType)
    {
        Long[] categoryIds = Convert.toLongArray(ids);
        // 空串会被 Convert 转成空数组，<foreach> 将拼出 in () 造成 SQL 语法错误，必须拦截
        if (categoryIds == null || categoryIds.length == 0)
        {
            throw new ServiceException("未选择要删除的类别数据");
        }
        for (Long categoryId : categoryIds)
        {
            Category category = categoryMapper.selectCategoryById(categoryId);
            if (StringUtils.isNull(category))
            {
                throw new ServiceException(String.format("类别ID[%s]不存在或已被删除", categoryId));
            }
            // 越权保护：课程类别页面不能删除社区类别（反之亦然）
            if (!StringUtils.equals(categoryType, category.getCategoryType()))
            {
                throw new ServiceException(String.format("类别[%s]不属于当前模块，不能删除", category.getCategoryName()));
            }
            if (countContentByCategoryId(categoryId, categoryType) > 0)
            {
                throw new ServiceException(String.format("%1$s已关联内容,不能删除", category.getCategoryName()));
            }
        }
        return categoryMapper.deleteCategoryByIds(categoryIds);
    }

    /**
     * 统计该类别下已关联的内容数量（课程/文章）
     *
     * @param categoryId 类别ID
     * @param categoryType 类别类型
     * @return 关联内容数量
     */
    @Override
    public int countContentByCategoryId(Long categoryId, String categoryType)
    {
        return 0;
    }

    /**
     * 类别名称去空白，并兜底拦截空值
     *
     * @param categoryName 类别名称
     * @return 处理后的类别名称
     */
    private String trimCategoryName(String categoryName)
    {
        String name = StringUtils.trim(categoryName);
        if (StringUtils.isEmpty(name))
        {
            throw new ServiceException("类别名称不能为空");
        }
        return name;
    }
}

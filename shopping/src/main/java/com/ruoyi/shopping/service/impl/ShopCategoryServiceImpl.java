package com.ruoyi.shopping.service.impl;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ruoyi.common.constant.UserConstants;
import com.ruoyi.common.core.text.Convert;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.shopping.domain.ShopCategory;
import com.ruoyi.shopping.mapper.ShopCategoryMapper;
import com.ruoyi.shopping.mapper.ShopProductMapper;
import com.ruoyi.shopping.service.IShopCategoryService;

/**
 * 积分商城-商品分类 服务层处理
 *
 * @author edu
 */
@Service
public class ShopCategoryServiceImpl implements IShopCategoryService
{
    @Autowired
    private ShopCategoryMapper shopCategoryMapper;

    @Autowired
    private ShopProductMapper shopProductMapper;

    @Override
    public List<ShopCategory> selectShopCategoryList(ShopCategory category)
    {
        ShopCategory query = category == null ? new ShopCategory() : category;
        query.setCategoryName(StringUtils.trim(query.getCategoryName()));
        return shopCategoryMapper.selectShopCategoryList(query);
    }

    @Override
    public ShopCategory selectShopCategoryById(Long categoryId)
    {
        return categoryId == null ? null : shopCategoryMapper.selectShopCategoryById(categoryId);
    }

    @Override
    public boolean checkShopCategoryNameUnique(ShopCategory category)
    {
        category.setCategoryName(StringUtils.trim(category.getCategoryName()));
        Long categoryId = StringUtils.isNull(category.getCategoryId()) ? -1L : category.getCategoryId();
        ShopCategory info = shopCategoryMapper.checkShopCategoryNameUnique(category);
        if (StringUtils.isNotNull(info) && info.getCategoryId().longValue() != categoryId.longValue())
        {
            return UserConstants.NOT_UNIQUE;
        }
        return UserConstants.UNIQUE;
    }

    @Override
    public int insertShopCategory(ShopCategory category)
    {
        fillDefaults(category);
        return shopCategoryMapper.insertShopCategory(category);
    }

    @Override
    public int updateShopCategory(ShopCategory category)
    {
        if (category.getCategoryId() == null)
        {
            throw new ServiceException("分类ID不能为空");
        }
        fillDefaults(category);
        return shopCategoryMapper.updateShopCategory(category);
    }

    /**
     * 删除分类前先挡一层：分类下还有未删除商品时不允许删，
     * 否则这些商品在用户端会因为没有可用分类而整体消失，管理员很难发现原因。
     */
    @Override
    @Transactional
    public int deleteShopCategoryByIds(String ids)
    {
        Long[] categoryIds = Convert.toLongArray(ids);
        if (categoryIds == null || categoryIds.length == 0)
        {
            throw new ServiceException("未选择要删除的分类");
        }
        for (Long categoryId : categoryIds)
        {
            if (categoryId == null)
            {
                continue;
            }
            ShopCategory category = shopCategoryMapper.selectShopCategoryById(categoryId);
            if (category == null)
            {
                continue;
            }
            int productCount = shopProductMapper.countByCategoryId(categoryId);
            if (productCount > 0)
            {
                throw new ServiceException("分类「" + category.getCategoryName() + "」下存在商品，不能删除");
            }
        }
        return shopCategoryMapper.deleteShopCategoryByIds(categoryIds);
    }

    /**
     * 必填项兜底：后台表单正常都会带，服务层再兜一次，避免直接调接口时写入 null。
     */
    private void fillDefaults(ShopCategory category)
    {
        category.setCategoryName(StringUtils.trim(category.getCategoryName()));
        if (category.getOrderNum() == null)
        {
            category.setOrderNum(0);
        }
        if (StringUtils.isEmpty(category.getStatus()))
        {
            category.setStatus(ShopCategory.STATUS_NORMAL);
        }
    }
}

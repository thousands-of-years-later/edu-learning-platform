package com.ruoyi.shopping.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.common.core.text.Convert;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.shopping.domain.ShopProduct;
import com.ruoyi.shopping.service.IShopProductService;

/**
 * 积分商城-商品 接口 v1（Token 认证，给 App / 小程序用）
 *
 * GET /api/v1/shop/product/list    商品列表（分页）
 * GET /api/v1/shop/product/detail  商品详情
 *
 * 只返回「上架 + 未删除 + 分类启用且未删除」的商品；停用/删除与不存在一样处理。
 *
 * @author edu
 */
@RestController
@RequestMapping("/api/v1/shop/product")
public class ApiV1ShopProductController extends BaseController
{
    @Autowired
    private IShopProductService shopProductService;

    /**
     * 商品列表（分页）
     *
     * @param product 过滤：categoryId、keyword（商品名模糊）；分页：pageNum/pageSize
     * @return {rows:[商品（含 categoryName、stock、soldCount）], total}
     */
    @GetMapping("/list")
    public TableDataInfo list(ShopProduct product)
    {
        // 校验/归一化在 startPage() 之前：PageHelper 的分页参数挂在 ThreadLocal 上，
        // 校验失败若发生在 startPage() 之后且没有查询消费它，参数会残留到本线程的下一条查询
        startPage();
        return getDataTable(shopProductService.selectVisibleProductList(product));
    }

    /**
     * 商品详情
     *
     * productId 用 String 接、手动转 Long：非法 ID 也要返回 JSON，
     * 交给 Spring 做 Long 绑定会抛类型转换异常、走到 HTML 错误页。
     *
     * @param productId 商品ID
     * @return {code:0, data:{...商品...}} 或 {code:500, msg:"商品不存在或已下架"}
     */
    @GetMapping("/detail")
    public AjaxResult detail(String productId)
    {
        Long id = Convert.toLong(productId);
        if (StringUtils.isNull(id))
        {
            return AjaxResult.error("商品ID不合法");
        }
        ShopProduct product = shopProductService.selectVisibleProductById(id);
        if (StringUtils.isNull(product))
        {
            return AjaxResult.error("商品不存在或已下架");
        }
        return AjaxResult.success(product);
    }
}

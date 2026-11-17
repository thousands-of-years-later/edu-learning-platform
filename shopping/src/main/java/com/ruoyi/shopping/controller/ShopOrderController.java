package com.ruoyi.shopping.controller;

import java.util.List;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.shopping.domain.ShopOrder;
import com.ruoyi.shopping.service.IShopOrderService;

/**
 * 积分商城-兑换记录（后台，只读）
 *
 * 兑换记录不可修改、不可删除；这里只提供列表与筛选。
 * 页面模板：templates/shop/order/order.html
 *
 * @author edu
 */
@Controller
@RequestMapping("/shop/order")
public class ShopOrderController extends BaseController
{
    private String prefix = "shop/order";

    @Autowired
    private IShopOrderService shopOrderService;

    @RequiresPermissions("shop:order:view")
    @GetMapping()
    public String order()
    {
        return prefix + "/order";
    }

    /**
     * 兑换记录列表
     *
     * 这里显式加权限：记录里含用户和兑换码，不给普通角色读。
     */
    @RequiresPermissions("shop:order:list")
    @PostMapping("/list")
    @ResponseBody
    public TableDataInfo list(ShopOrder order)
    {
        startPage();
        List<ShopOrder> list = shopOrderService.selectShopOrderList(order);
        return getDataTable(list);
    }
}

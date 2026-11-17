package com.ruoyi.shopping.controller;

import java.util.ArrayList;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.ruoyi.common.core.api.ApiContext;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.shopping.service.IShopOrderService;

/**
 * 积分商城-我的兑换记录 接口 v1（Token 认证）
 *
 * GET /api/v1/shop/order/list
 *
 * 只能看自己的记录：userId 取 ApiContext，接口不接受 userId 参数。
 *
 * @author edu
 */
@RestController
@RequestMapping("/api/v1/shop/order")
public class ApiV1ShopOrderController extends BaseController
{
    @Autowired
    private IShopOrderService shopOrderService;

    /**
     * 我的兑换记录（分页，按兑换时间倒序）
     *
     * @return {rows:[{orderId, redemptionCode, productId, productName, coverUrl, pointsCost, createTime}], total}
     */
    @GetMapping("/list")
    public TableDataInfo list()
    {
        Long userId = ApiContext.getUserId();
        // 理论上过滤器已经保证有用户；这里兜底时不要 startPage()，
        // 否则分页参数会残留到本线程的下一条查询（PageHelper ThreadLocal 陷阱）
        if (userId == null)
        {
            return getDataTable(new ArrayList<>());
        }
        startPage();
        return getDataTable(shopOrderService.selectByUser(userId));
    }
}

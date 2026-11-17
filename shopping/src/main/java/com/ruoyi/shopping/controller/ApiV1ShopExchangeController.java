package com.ruoyi.shopping.controller;

import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.ruoyi.common.core.api.ApiContext;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.text.Convert;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.shopping.service.IShopOrderService;

/**
 * 积分商城-兑换 接口 v1（Token 认证，给 App / 小程序用）
 *
 * POST /api/v1/shop/exchange
 *
 * 用户ID只取 ApiContext（ApiTokenFilter 校验 Token 后写入），绝不接受前端传入。
 * 兑换成功当场返回兑换码；扣库存、写订单、扣积分在同一事务内完成。
 *
 * @author edu
 */
@RestController
@RequestMapping("/api/v1/shop")
public class ApiV1ShopExchangeController extends BaseController
{
    @Autowired
    private IShopOrderService shopOrderService;

    /**
     * 兑换商品（表单）
     *
     * @param productId 商品ID
     * @return {code:0, msg:"兑换成功", orderId, redemptionCode, productId, productName, pointsCost, balance}
     */
    @PostMapping("/exchange")
    public AjaxResult exchange(String productId)
    {
        Long id = Convert.toLong(productId);
        if (StringUtils.isNull(id))
        {
            return AjaxResult.error("商品ID不合法");
        }
        AjaxResult ajax = AjaxResult.success("兑换成功");
        ajax.putAll(shopOrderService.exchange(ApiContext.getUserId(), id));
        return ajax;
    }

    /**
     * 兑换商品（JSON 请求体）
     *
     * @param body 字段同表单：productId
     */
    @PostMapping(value = "/exchange", consumes = MediaType.APPLICATION_JSON_VALUE)
    public AjaxResult exchangeJson(@RequestBody(required = false) Map<String, String> body)
    {
        if (body == null)
        {
            return AjaxResult.error("请求参数不能为空");
        }
        return exchange(body.get("productId"));
    }
}

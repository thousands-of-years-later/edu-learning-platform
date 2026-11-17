package com.ruoyi.shopping.controller;

import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.ruoyi.common.core.api.ApiContext;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.common.core.text.Convert;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.shopping.domain.ShopAddress;
import com.ruoyi.shopping.service.IShopAddressService;

/**
 * 积分商城-收货地址 接口 v1（Token 认证，给 App / 小程序用）
 *
 * GET  /api/v1/shop/address/list    我的地址列表
 * GET  /api/v1/shop/address/detail  地址详情
 * POST /api/v1/shop/address/add     新增地址
 * POST /api/v1/shop/address/edit    修改地址
 * POST /api/v1/shop/address/remove  删除地址
 *
 * 地址与兑换流程无关（不做配送），只是用户地址簿；
 * userId 只取 ApiContext，所有读写都按 userId 隔离，越权一律返回「地址不存在」。
 *
 * @author edu
 */
@RestController
@RequestMapping("/api/v1/shop/address")
public class ApiV1ShopAddressController extends BaseController
{
    @Autowired
    private IShopAddressService shopAddressService;

    /**
     * 我的地址列表（不分页，默认地址在前）
     *
     * @return {rows:[{addressId, receiverName, receiverPhone, detailAddress, isDefault, ...}], total}
     */
    @GetMapping("/list")
    public TableDataInfo list()
    {
        return getDataTable(shopAddressService.selectByUser(ApiContext.getUserId()));
    }

    /**
     * 地址详情
     *
     * GET 里校验失败直接返回 AjaxResult，不抛 ServiceException（否则会走 HTML 错误页）。
     *
     * @param addressId 地址ID
     */
    @GetMapping("/detail")
    public AjaxResult detail(String addressId)
    {
        Long id = Convert.toLong(addressId);
        if (StringUtils.isNull(id))
        {
            return AjaxResult.error("地址ID不合法");
        }
        ShopAddress address = shopAddressService.selectByIdAndUser(ApiContext.getUserId(), id);
        if (StringUtils.isNull(address))
        {
            return AjaxResult.error("地址不存在");
        }
        return AjaxResult.success(address);
    }

    /**
     * 新增地址（表单）
     *
     * @param receiverName  收货人
     * @param receiverPhone 手机号
     * @param detailAddress 详细地址
     * @param isDefault     是否默认：1/true/yes/on（第一条地址会自动成为默认）
     */
    @PostMapping("/add")
    public AjaxResult add(String receiverName, String receiverPhone, String detailAddress, String isDefault)
    {
        ShopAddress address = new ShopAddress();
        fillAddress(address, receiverName, receiverPhone, detailAddress, isDefault);
        return AjaxResult.success("保存成功", shopAddressService.add(ApiContext.getUserId(), address));
    }

    /**
     * 新增地址（JSON 请求体）
     *
     * @param body 字段同表单：receiverName、receiverPhone、detailAddress、isDefault
     */
    @PostMapping(value = "/add", consumes = MediaType.APPLICATION_JSON_VALUE)
    public AjaxResult addJson(@RequestBody(required = false) Map<String, String> body)
    {
        if (body == null)
        {
            return AjaxResult.error("请求参数不能为空");
        }
        return add(body.get("receiverName"), body.get("receiverPhone"),
                body.get("detailAddress"), body.get("isDefault"));
    }

    /**
     * 修改地址（表单）
     *
     * @param addressId     地址ID
     * @param receiverName  收货人
     * @param receiverPhone 手机号
     * @param detailAddress 详细地址
     * @param isDefault     是否默认；不传 = 保持原值，传 0 = 取消默认
     */
    @PostMapping("/edit")
    public AjaxResult edit(String addressId, String receiverName, String receiverPhone,
            String detailAddress, String isDefault)
    {
        Long id = Convert.toLong(addressId);
        if (StringUtils.isNull(id))
        {
            return AjaxResult.error("地址ID不合法");
        }
        ShopAddress address = new ShopAddress();
        address.setAddressId(id);
        fillAddress(address, receiverName, receiverPhone, detailAddress, isDefault);
        return AjaxResult.success("保存成功", shopAddressService.edit(ApiContext.getUserId(), address));
    }

    /**
     * 修改地址（JSON 请求体）
     *
     * @param body 字段同表单：addressId、receiverName、receiverPhone、detailAddress、isDefault
     */
    @PostMapping(value = "/edit", consumes = MediaType.APPLICATION_JSON_VALUE)
    public AjaxResult editJson(@RequestBody(required = false) Map<String, String> body)
    {
        if (body == null)
        {
            return AjaxResult.error("请求参数不能为空");
        }
        Long id = Convert.toLong(body.get("addressId"));
        if (StringUtils.isNull(id))
        {
            return AjaxResult.error("地址ID不合法");
        }
        ShopAddress address = new ShopAddress();
        address.setAddressId(id);
        fillAddress(address, body.get("receiverName"), body.get("receiverPhone"),
                body.get("detailAddress"), body.get("isDefault"));
        return AjaxResult.success("保存成功", shopAddressService.edit(ApiContext.getUserId(), address));
    }

    /**
     * 删除地址（表单）
     *
     * @param addressId 地址ID
     */
    @PostMapping("/remove")
    public AjaxResult remove(String addressId)
    {
        Long id = Convert.toLong(addressId);
        if (StringUtils.isNull(id))
        {
            return AjaxResult.error("地址ID不合法");
        }
        return toAjax(shopAddressService.remove(ApiContext.getUserId(), id));
    }

    /**
     * 删除地址（JSON 请求体）
     *
     * @param body 字段同表单：addressId
     */
    @PostMapping(value = "/remove", consumes = MediaType.APPLICATION_JSON_VALUE)
    public AjaxResult removeJson(@RequestBody(required = false) Map<String, String> body)
    {
        if (body == null)
        {
            return AjaxResult.error("请求参数不能为空");
        }
        return remove(body.get("addressId"));
    }

    private void fillAddress(ShopAddress address, String receiverName, String receiverPhone,
            String detailAddress, String isDefault)
    {
        address.setReceiverName(receiverName);
        address.setReceiverPhone(receiverPhone);
        address.setDetailAddress(detailAddress);
        address.setIsDefault(isDefault);
    }
}

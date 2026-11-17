package com.ruoyi.shopping.service;

import java.util.List;
import com.ruoyi.shopping.domain.ShopAddress;

/**
 * 积分商城-收货地址 服务层
 *
 * userId 一律由调用方从 API 上下文带入；所有读写都按 userId 隔离。
 *
 * @author edu
 */
public interface IShopAddressService
{
    /**
     * 某个用户的地址列表（默认地址在前）
     */
    public List<ShopAddress> selectByUser(Long userId);

    /**
     * 按ID + 用户查地址；不存在/不是本人返回 null
     *
     * 返回 null 而不是抛 ServiceException：GET 详情接口需要在控制器里直接返回 AjaxResult，
     * 抛业务异常会走到 HTML 错误页（见 GlobalExceptionHandler 的 GET 分支）。
     */
    public ShopAddress selectByIdAndUser(Long userId, Long addressId);

    /**
     * 新增地址；第一条地址自动设为默认
     */
    public ShopAddress add(Long userId, ShopAddress address);

    /**
     * 修改地址；isDefault 不传时保持原值
     */
    public ShopAddress edit(Long userId, ShopAddress address);

    /**
     * 逻辑删除地址
     *
     * @return 影响行数（1）
     */
    public int remove(Long userId, Long addressId);
}

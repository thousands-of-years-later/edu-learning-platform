package com.ruoyi.shopping.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.ruoyi.shopping.domain.ShopAddress;

/**
 * 积分商城-收货地址 数据层
 *
 * 所有按 ID 的查询/更新/删除都带 user_id：0 行即「不存在或不是本人」，控制器/服务层统一报「地址不存在」。
 *
 * @author edu
 */
public interface ShopAddressMapper
{
    /**
     * 某个用户的地址列表（默认地址在前）
     */
    public List<ShopAddress> selectByUser(@Param("userId") Long userId);

    /**
     * 按ID + 用户查未删除地址（越权也返回 null）
     */
    public ShopAddress selectByIdAndUser(@Param("addressId") Long addressId, @Param("userId") Long userId);

    /**
     * 某用户未删除地址数量（判断是否第一条地址）
     */
    public int countByUser(@Param("userId") Long userId);

    /**
     * 把该用户现有默认地址清成 NULL（设置新默认前调用）
     */
    public int clearDefault(@Param("userId") Long userId);

    public int insertShopAddress(ShopAddress address);

    /**
     * 更新地址（where 带 user_id）
     */
    public int updateShopAddress(ShopAddress address);

    /**
     * 逻辑删除（同时把 is_default 置 NULL，避免唯一键挡住后续默认地址）
     */
    public int deleteByIdAndUser(@Param("addressId") Long addressId, @Param("userId") Long userId);
}

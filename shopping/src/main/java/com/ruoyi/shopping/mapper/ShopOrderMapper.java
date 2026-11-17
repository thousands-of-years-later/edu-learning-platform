package com.ruoyi.shopping.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.ruoyi.shopping.domain.ShopOrder;

/**
 * 积分商城-兑换记录 数据层
 *
 * 只有查询和新增：兑换记录不可修改、不可删除。
 *
 * @author edu
 */
public interface ShopOrderMapper
{
    /**
     * 后台兑换记录列表（含用户昵称，分页由控制器 startPage() 驱动）
     */
    public List<ShopOrder> selectShopOrderList(ShopOrder order);

    /**
     * 某个用户的兑换记录（用户端「我的兑换」）
     */
    public List<ShopOrder> selectByUser(@Param("userId") Long userId);

    /**
     * 新增兑换记录，回填 orderId（用于积分流水 ref_id）
     */
    public int insertShopOrder(ShopOrder order);
}

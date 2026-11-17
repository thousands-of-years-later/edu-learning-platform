package com.ruoyi.shopping.service.impl;

import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.shopping.domain.ShopAddress;
import com.ruoyi.shopping.mapper.ShopAddressMapper;
import com.ruoyi.shopping.service.IShopAddressService;

/**
 * 积分商城-收货地址 服务层处理
 *
 * 默认地址的存储约定：库里只存 '1'（默认）或 NULL（非默认），
 * 配合唯一键 (user_id, is_default) 保证每用户最多一个默认地址。
 * 因此设置新默认前必须先 clearDefault，取消默认/删除时必须写 NULL 而不是 '0'。
 *
 * @author edu
 */
@Service
public class ShopAddressServiceImpl implements IShopAddressService
{
    /** 手机号：当前只支持大陆手机号 */
    private static final String PHONE_PATTERN = "^1[3-9]\\d{9}$";

    @Autowired
    private ShopAddressMapper shopAddressMapper;

    @Override
    public List<ShopAddress> selectByUser(Long userId)
    {
        if (userId == null)
        {
            return new ArrayList<>();
        }
        return shopAddressMapper.selectByUser(userId);
    }

    @Override
    public ShopAddress selectByIdAndUser(Long userId, Long addressId)
    {
        if (userId == null || addressId == null)
        {
            return null;
        }
        return shopAddressMapper.selectByIdAndUser(addressId, userId);
    }

    @Override
    @Transactional
    public ShopAddress add(Long userId, ShopAddress address)
    {
        checkUser(userId);
        if (address == null)
        {
            throw new ServiceException("请求参数不能为空");
        }
        validateAddress(address);

        // 第一条地址无论客户端传什么，都强制设为默认；之后按客户端意图处理
        boolean firstAddress = shopAddressMapper.countByUser(userId) == 0;
        String defaultFlag = parseDefaultFlag(address.getIsDefault(), false);
        if (firstAddress)
        {
            defaultFlag = ShopAddress.DEFAULT_YES;
        }

        if (ShopAddress.DEFAULT_YES.equals(defaultFlag))
        {
            shopAddressMapper.clearDefault(userId);
            address.setIsDefault(ShopAddress.DEFAULT_YES);
        }
        else
        {
            address.setIsDefault(null);
        }

        address.setUserId(userId);
        try
        {
            shopAddressMapper.insertShopAddress(address);
        }
        catch (DuplicateKeyException e)
        {
            // 并发设置默认地址时，唯一键会挡住第二条
            throw new ServiceException("操作过于频繁，请重试");
        }
        return shopAddressMapper.selectByIdAndUser(address.getAddressId(), userId);
    }

    @Override
    @Transactional
    public ShopAddress edit(Long userId, ShopAddress address)
    {
        checkUser(userId);
        if (address == null || address.getAddressId() == null)
        {
            throw new ServiceException("地址ID不能为空");
        }

        ShopAddress existing = shopAddressMapper.selectByIdAndUser(address.getAddressId(), userId);
        if (existing == null)
        {
            throw new ServiceException("地址不存在");
        }
        validateAddress(address);

        // 编辑时 isDefault 缺省 = 保持原值；显式 0 = 取消默认；显式 1 = 设为默认
        String defaultFlag = parseDefaultFlag(address.getIsDefault(), true);
        if (defaultFlag == null)
        {
            // 库里非默认存 NULL，这里必须还原成 NULL，不能写 "0"（否则会撞唯一键）
            address.setIsDefault(ShopAddress.DEFAULT_YES.equals(existing.getIsDefault())
                    ? ShopAddress.DEFAULT_YES : null);
        }
        else if (ShopAddress.DEFAULT_YES.equals(defaultFlag))
        {
            shopAddressMapper.clearDefault(userId);
            address.setIsDefault(ShopAddress.DEFAULT_YES);
        }
        else
        {
            address.setIsDefault(null);
        }

        address.setUserId(userId);
        try
        {
            int rows = shopAddressMapper.updateShopAddress(address);
            if (rows == 0)
            {
                throw new ServiceException("地址不存在");
            }
        }
        catch (DuplicateKeyException e)
        {
            throw new ServiceException("操作过于频繁，请重试");
        }
        return shopAddressMapper.selectByIdAndUser(address.getAddressId(), userId);
    }

    @Override
    @Transactional
    public int remove(Long userId, Long addressId)
    {
        checkUser(userId);
        if (addressId == null)
        {
            throw new ServiceException("地址ID不能为空");
        }
        int rows = shopAddressMapper.deleteByIdAndUser(addressId, userId);
        if (rows == 0)
        {
            throw new ServiceException("地址不存在");
        }
        return rows;
    }

    private void checkUser(Long userId)
    {
        if (userId == null)
        {
            throw new ServiceException("请先登录");
        }
    }

    /**
     * 收货人/手机号/详细地址校验（服务层兜底，API 控制器不用再重复写一遍）
     */
    private void validateAddress(ShopAddress address)
    {
        String receiverName = StringUtils.trim(address.getReceiverName());
        if (StringUtils.isEmpty(receiverName))
        {
            throw new ServiceException("收货人不能为空");
        }
        if (receiverName.length() > 30)
        {
            throw new ServiceException("收货人长度不能超过30个字符");
        }
        address.setReceiverName(receiverName);

        String receiverPhone = StringUtils.trim(address.getReceiverPhone());
        if (StringUtils.isEmpty(receiverPhone))
        {
            throw new ServiceException("手机号不能为空");
        }
        if (!receiverPhone.matches(PHONE_PATTERN))
        {
            throw new ServiceException("请输入正确的手机号");
        }
        address.setReceiverPhone(receiverPhone);

        String detailAddress = StringUtils.trim(address.getDetailAddress());
        if (StringUtils.isEmpty(detailAddress))
        {
            throw new ServiceException("详细地址不能为空");
        }
        if (detailAddress.length() > 200)
        {
            throw new ServiceException("详细地址长度不能超过200个字符");
        }
        address.setDetailAddress(detailAddress);
    }

    /**
     * 归一化默认地址标记。
     *
     * @param raw             请求值（"1"/"0"/"true"/"false"/"on"...）
     * @param blankMeansClear 空串是否按「取消默认」处理（编辑场景）；新增场景空串按「未指定」处理
     * @return "1" / "0" / null（未指定）
     */
    private String parseDefaultFlag(String raw, boolean blankMeansClear)
    {
        if (raw == null)
        {
            return null;
        }
        String value = raw.trim().toLowerCase();
        if (value.isEmpty())
        {
            return blankMeansClear ? ShopAddress.DEFAULT_NO : null;
        }
        if ("1".equals(value) || "true".equals(value) || "yes".equals(value) || "on".equals(value))
        {
            return ShopAddress.DEFAULT_YES;
        }
        if ("0".equals(value) || "false".equals(value) || "no".equals(value) || "off".equals(value))
        {
            return ShopAddress.DEFAULT_NO;
        }
        throw new ServiceException("默认地址标记不合法");
    }
}

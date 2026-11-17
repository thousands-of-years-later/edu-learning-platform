package com.ruoyi.shopping.domain;

import java.io.Serializable;
import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;

/**
 * 积分商城-收货地址 learning_shop_address
 *
 * 与兑换流程无关（需求：不用实现配送），只作为用户地址簿。
 * is_default 在库里只有 '1'（默认）或 NULL（非默认），
 * 配合唯一键 (user_id, is_default) 保证每个用户最多一个默认地址。
 *
 * @author edu
 */
public class ShopAddress implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 默认地址：是 */
    public static final String DEFAULT_YES = "1";

    /** 默认地址：否（对外输出用，库里存 NULL） */
    public static final String DEFAULT_NO = "0";

    /** 删除标志：存在 */
    public static final String DEL_FLAG_NORMAL = "0";

    /** 删除标志：删除 */
    public static final String DEL_FLAG_DELETED = "2";

    /** 地址ID */
    private Long addressId;

    /** 用户ID（sys_user.user_id） */
    private Long userId;

    /** 收货人 */
    private String receiverName;

    /** 收货人手机号 */
    private String receiverPhone;

    /** 详细地址 */
    private String detailAddress;

    /** 是否默认（1默认；0非默认，库里存 NULL） */
    private String isDefault;

    /** 删除标志（0存在 2删除） */
    private String delFlag;

    /** 创建时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date createTime;

    /** 更新时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date updateTime;

    public Long getAddressId()
    {
        return addressId;
    }

    public void setAddressId(Long addressId)
    {
        this.addressId = addressId;
    }

    public Long getUserId()
    {
        return userId;
    }

    public void setUserId(Long userId)
    {
        this.userId = userId;
    }

    public String getReceiverName()
    {
        return receiverName;
    }

    public void setReceiverName(String receiverName)
    {
        this.receiverName = receiverName;
    }

    public String getReceiverPhone()
    {
        return receiverPhone;
    }

    public void setReceiverPhone(String receiverPhone)
    {
        this.receiverPhone = receiverPhone;
    }

    public String getDetailAddress()
    {
        return detailAddress;
    }

    public void setDetailAddress(String detailAddress)
    {
        this.detailAddress = detailAddress;
    }

    public String getIsDefault()
    {
        return isDefault;
    }

    public void setIsDefault(String isDefault)
    {
        this.isDefault = isDefault;
    }

    public String getDelFlag()
    {
        return delFlag;
    }

    public void setDelFlag(String delFlag)
    {
        this.delFlag = delFlag;
    }

    public Date getCreateTime()
    {
        return createTime;
    }

    public void setCreateTime(Date createTime)
    {
        this.createTime = createTime;
    }

    public Date getUpdateTime()
    {
        return updateTime;
    }

    public void setUpdateTime(Date updateTime)
    {
        this.updateTime = updateTime;
    }
}

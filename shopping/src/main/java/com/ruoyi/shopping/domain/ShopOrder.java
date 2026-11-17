package com.ruoyi.shopping.domain;

import java.io.Serializable;
import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;

/**
 * 积分商城-兑换记录 learning_shop_order
 *
 * 只增不改：兑换成功即扣积分、扣库存并生成唯一兑换码。
 * 不存收货地址、不存配送/核销状态（需求明确不做配送）。
 * product_name / cover_url / points_cost 是下单快照，商品后续改名或删除不影响历史记录。
 *
 * @author edu
 */
public class ShopOrder implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 兑换记录ID */
    private Long orderId;

    /** 兑换码（全局唯一） */
    private String redemptionCode;

    /** 用户ID（sys_user.user_id） */
    private Long userId;

    /** 用户昵称（后台列表展示字段，非本表列） */
    private String userName;

    /** 商品ID */
    private Long productId;

    /** 商品名称快照 */
    private String productName;

    /** 商品封面快照 */
    private String coverUrl;

    /** 消耗积分快照 */
    private Integer pointsCost;

    /** 兑换时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date createTime;

    public Long getOrderId()
    {
        return orderId;
    }

    public void setOrderId(Long orderId)
    {
        this.orderId = orderId;
    }

    public String getRedemptionCode()
    {
        return redemptionCode;
    }

    public void setRedemptionCode(String redemptionCode)
    {
        this.redemptionCode = redemptionCode;
    }

    public Long getUserId()
    {
        return userId;
    }

    public void setUserId(Long userId)
    {
        this.userId = userId;
    }

    public String getUserName()
    {
        return userName;
    }

    public void setUserName(String userName)
    {
        this.userName = userName;
    }

    public Long getProductId()
    {
        return productId;
    }

    public void setProductId(Long productId)
    {
        this.productId = productId;
    }

    public String getProductName()
    {
        return productName;
    }

    public void setProductName(String productName)
    {
        this.productName = productName;
    }

    public String getCoverUrl()
    {
        return coverUrl;
    }

    public void setCoverUrl(String coverUrl)
    {
        this.coverUrl = coverUrl;
    }

    public Integer getPointsCost()
    {
        return pointsCost;
    }

    public void setPointsCost(Integer pointsCost)
    {
        this.pointsCost = pointsCost;
    }

    public Date getCreateTime()
    {
        return createTime;
    }

    public void setCreateTime(Date createTime)
    {
        this.createTime = createTime;
    }
}

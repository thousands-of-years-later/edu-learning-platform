package com.ruoyi.shopping.domain;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import com.ruoyi.common.core.domain.BaseEntity;

/**
 * 积分商城-商品 learning_shop_product
 *
 * @author edu
 */
public class ShopProduct extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 状态：上架（正常） */
    public static final String STATUS_NORMAL = "0";

    /** 状态：下架（停用） */
    public static final String STATUS_DISABLED = "1";

    /** 删除标志：存在 */
    public static final String DEL_FLAG_NORMAL = "0";

    /** 删除标志：删除 */
    public static final String DEL_FLAG_DELETED = "2";

    /** 商品ID */
    private Long productId;

    /** 分类ID */
    private Long categoryId;

    /** 商品名称 */
    private String productName;

    /** 商品封面（相对路径或完整 OSS 地址） */
    private String coverUrl;

    /** 兑换所需积分 */
    private Integer pointsPrice;

    /** 库存 */
    private Integer stock;

    /** 已兑换数量 */
    private Integer soldCount;

    /** 商品描述 */
    private String description;

    /** 显示顺序 */
    private Integer orderNum;

    /** 状态（0上架 1下架） */
    private String status;

    /** 删除标志（0存在 2删除） */
    private String delFlag;

    /** 分类名称（展示字段，不入库） */
    private String categoryName;

    /** 用户端关键字（商品名模糊，展示字段/查询参数，不入库） */
    private String keyword;

    public Long getProductId()
    {
        return productId;
    }

    public void setProductId(Long productId)
    {
        this.productId = productId;
    }

    @NotNull(message = "商品分类不能为空")
    public Long getCategoryId()
    {
        return categoryId;
    }

    public void setCategoryId(Long categoryId)
    {
        this.categoryId = categoryId;
    }

    @NotBlank(message = "商品名称不能为空")
    @Size(min = 0, max = 100, message = "商品名称长度不能超过100个字符")
    public String getProductName()
    {
        return productName;
    }

    public void setProductName(String productName)
    {
        this.productName = productName;
    }

    @Size(min = 0, max = 500, message = "商品封面地址长度不能超过500个字符")
    public String getCoverUrl()
    {
        return coverUrl;
    }

    public void setCoverUrl(String coverUrl)
    {
        this.coverUrl = coverUrl;
    }

    @NotNull(message = "兑换积分不能为空")
    @Min(value = 1, message = "兑换积分必须大于 0")
    public Integer getPointsPrice()
    {
        return pointsPrice;
    }

    public void setPointsPrice(Integer pointsPrice)
    {
        this.pointsPrice = pointsPrice;
    }

    @NotNull(message = "库存不能为空")
    @Min(value = 0, message = "库存不能小于 0")
    public Integer getStock()
    {
        return stock;
    }

    public void setStock(Integer stock)
    {
        this.stock = stock;
    }

    public Integer getSoldCount()
    {
        return soldCount;
    }

    public void setSoldCount(Integer soldCount)
    {
        this.soldCount = soldCount;
    }

    @Size(min = 0, max = 2000, message = "商品描述长度不能超过2000个字符")
    public String getDescription()
    {
        return description;
    }

    public void setDescription(String description)
    {
        this.description = description;
    }

    public Integer getOrderNum()
    {
        return orderNum;
    }

    public void setOrderNum(Integer orderNum)
    {
        this.orderNum = orderNum;
    }

    @Size(min = 0, max = 1, message = "状态长度不能超过1个字符")
    public String getStatus()
    {
        return status;
    }

    public void setStatus(String status)
    {
        this.status = status;
    }

    public String getDelFlag()
    {
        return delFlag;
    }

    public void setDelFlag(String delFlag)
    {
        this.delFlag = delFlag;
    }

    public String getCategoryName()
    {
        return categoryName;
    }

    public void setCategoryName(String categoryName)
    {
        this.categoryName = categoryName;
    }

    public String getKeyword()
    {
        return keyword;
    }

    public void setKeyword(String keyword)
    {
        this.keyword = keyword;
    }

    @Override
    public String toString()
    {
        return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
            .append("productId", getProductId())
            .append("categoryId", getCategoryId())
            .append("productName", getProductName())
            .append("coverUrl", getCoverUrl())
            .append("pointsPrice", getPointsPrice())
            .append("stock", getStock())
            .append("soldCount", getSoldCount())
            .append("description", getDescription())
            .append("orderNum", getOrderNum())
            .append("status", getStatus())
            .append("delFlag", getDelFlag())
            .append("categoryName", getCategoryName())
            .append("createBy", getCreateBy())
            .append("createTime", getCreateTime())
            .append("updateBy", getUpdateBy())
            .append("updateTime", getUpdateTime())
            .append("remark", getRemark())
            .toString();
    }
}

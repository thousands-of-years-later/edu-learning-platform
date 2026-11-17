package com.ruoyi.user.domain;

import java.util.ArrayList;
import java.util.List;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import com.ruoyi.common.core.domain.BaseEntity;

/**
 * 平台协议 learning_agreement
 *
 * 固定三种协议：用户协议、隐私政策、关于我们。
 * 业务上每种类型只保留一条未删除记录（新增/修改时由服务层做类型查重），
 * 删除是软删除，删掉后可以重新新增同类型记录。
 *
 * 协议正文以 HTML 字符串存储（后台用 summernote 编辑），
 * 列表接口不返回 content，只有按ID查询（编辑页）才带正文，避免列表把大字段全查出来。
 *
 * @author edu
 */
public class Agreement extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 协议类型：用户协议 */
    public static final String TYPE_USER_AGREEMENT = "user_agreement";

    /** 协议类型：隐私政策 */
    public static final String TYPE_PRIVACY_POLICY = "privacy_policy";

    /** 协议类型：关于我们 */
    public static final String TYPE_ABOUT_US = "about_us";

    /** 状态：正常 */
    public static final String STATUS_NORMAL = "0";

    /** 状态：停用 */
    public static final String STATUS_DISABLED = "1";

    /** 删除标志：存在 */
    public static final String DEL_FLAG_NORMAL = "0";

    /** 删除标志：删除 */
    public static final String DEL_FLAG_DELETED = "2";

    /** 协议ID */
    private Long agreementId;

    /** 协议类型（user_agreement / privacy_policy / about_us） */
    private String agreementType;

    /** 协议标题 */
    private String title;

    /** 协议正文（HTML） */
    private String content;

    /** 显示顺序 */
    private Integer orderNum;

    /** 状态（0正常 1停用） */
    private String status;

    /** 删除标志（0存在 2删除） */
    private String delFlag;

    /**
     * 协议类型是否合法
     *
     * @param agreementType 协议类型
     * @return true 合法；false 不是约定的三种类型
     */
    public static boolean isAllowedType(String agreementType)
    {
        return TYPE_USER_AGREEMENT.equals(agreementType)
                || TYPE_PRIVACY_POLICY.equals(agreementType)
                || TYPE_ABOUT_US.equals(agreementType);
    }

    /**
     * 协议类型转中文名（后台列表展示用；未知类型原样返回，方便排查脏数据）
     *
     * @param agreementType 协议类型
     * @return 中文名
     */
    public static String typeLabelOf(String agreementType)
    {
        if (TYPE_USER_AGREEMENT.equals(agreementType))
        {
            return "用户协议";
        }
        if (TYPE_PRIVACY_POLICY.equals(agreementType))
        {
            return "隐私政策";
        }
        if (TYPE_ABOUT_US.equals(agreementType))
        {
            return "关于我们";
        }
        return agreementType;
    }

    /**
     * 三种协议类型选项（新增/编辑/查询下拉框用）
     *
     * @return 按固定顺序返回的类型选项
     */
    public static List<TypeOption> typeOptions()
    {
        List<TypeOption> options = new ArrayList<TypeOption>(3);
        options.add(new TypeOption(TYPE_USER_AGREEMENT, "用户协议"));
        options.add(new TypeOption(TYPE_PRIVACY_POLICY, "隐私政策"));
        options.add(new TypeOption(TYPE_ABOUT_US, "关于我们"));
        return options;
    }

    public Long getAgreementId()
    {
        return agreementId;
    }

    public void setAgreementId(Long agreementId)
    {
        this.agreementId = agreementId;
    }

    @NotBlank(message = "协议类型不能为空")
    @Size(min = 0, max = 32, message = "协议类型长度不能超过32个字符")
    public String getAgreementType()
    {
        return agreementType;
    }

    public void setAgreementType(String agreementType)
    {
        this.agreementType = agreementType;
    }

    /**
     * 协议类型中文名（不落库，列表 JSON 直接带出，前端无需再维护一份映射）
     */
    public String getTypeLabel()
    {
        return typeLabelOf(agreementType);
    }

    @NotBlank(message = "协议标题不能为空")
    @Size(min = 0, max = 100, message = "协议标题长度不能超过100个字符")
    public String getTitle()
    {
        return title;
    }

    public void setTitle(String title)
    {
        this.title = title;
    }

    @NotBlank(message = "协议内容不能为空")
    public String getContent()
    {
        return content;
    }

    public void setContent(String content)
    {
        this.content = content;
    }

    @Min(value = 0, message = "显示顺序不能小于0")
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

    @Override
    public String toString()
    {
        return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
            .append("agreementId", getAgreementId())
            .append("agreementType", getAgreementType())
            .append("title", getTitle())
            .append("orderNum", getOrderNum())
            .append("status", getStatus())
            .append("delFlag", getDelFlag())
            .append("createBy", getCreateBy())
            .append("createTime", getCreateTime())
            .append("updateBy", getUpdateBy())
            .append("updateTime", getUpdateTime())
            .append("remark", getRemark())
            .toString();
    }

    /**
     * 类型下拉框选项（只读值对象，供 Thymeleaf 渲染）
     */
    public static class TypeOption
    {
        /** 类型编码（存库值） */
        private final String value;

        /** 类型中文名 */
        private final String label;

        public TypeOption(String value, String label)
        {
            this.value = value;
            this.label = label;
        }

        public String getValue()
        {
            return value;
        }

        public String getLabel()
        {
            return label;
        }
    }
}

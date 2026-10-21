package com.ruoyi.user.domain;

import java.io.Serializable;

/**
 * 用户资料（编辑主页 / 我的主页用）
 *
 * 不是一张表：昵称、头像、性别来自 sys_user（若依的用户表，后台用户管理也用这三个字段），
 * 个人简介来自 learning_user_profile（本模块扩展表）。
 * 因此这里只是【聚合出来给接口用】的传输对象，没有对应的 insert/update 语句，
 * 写操作分别落在 updateUserBasic / upsertBio 两条语句上。
 *
 * 头像是完整可访问地址（OSS 已开启时是 https://bucket.oss-.../upload/...）。
 *
 * @author edu
 */
public class UserProfile implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 性别：不公开（与 sys_dict_data 的 sys_user_sex 一致：0男 1女 2未知） */
    public static final String SEX_UNKNOWN = "2";

    /** 昵称最大长度（sys_user.user_name 是 varchar(30)，这里留余量并按产品口径卡 20） */
    public static final int MAX_USER_NAME_LENGTH = 20;

    /** 个人简介最大长度，必须与 learning_user_profile.bio 的 varchar(200) 保持一致 */
    public static final int MAX_BIO_LENGTH = 200;

    /** 头像地址最大长度，必须与 sys_user.avatar 的 varchar(500) 保持一致 */
    public static final int MAX_AVATAR_LENGTH = 500;

    private Long userId;

    /** 昵称（sys_user.user_name） */
    private String userName;

    /** 头像地址（sys_user.avatar），没设置时为 null，前端用昵称首字兜底 */
    private String avatar;

    /** 性别 0男 1女 2未知（sys_user.sex） */
    private String sex;

    /** 性别文案（服务层按 sex 换算，方便前端直接显示） */
    private String sexLabel;

    /** 个人简介（learning_user_profile.bio） */
    private String bio;

    /**
     * 性别值是否合法（只认 0 / 1 / 2）
     */
    public static boolean isValidSex(String sex)
    {
        return "0".equals(sex) || "1".equals(sex) || SEX_UNKNOWN.equals(sex);
    }

    /** 性别文案 */
    public static String sexLabelOf(String sex)
    {
        if ("0".equals(sex))
        {
            return "男";
        }
        if ("1".equals(sex))
        {
            return "女";
        }
        return "不公开";
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

    public String getAvatar()
    {
        return avatar;
    }

    public void setAvatar(String avatar)
    {
        this.avatar = avatar;
    }

    public String getSex()
    {
        return sex;
    }

    public void setSex(String sex)
    {
        this.sex = sex;
    }

    public String getSexLabel()
    {
        return sexLabel;
    }

    public void setSexLabel(String sexLabel)
    {
        this.sexLabel = sexLabel;
    }

    public String getBio()
    {
        return bio;
    }

    public void setBio(String bio)
    {
        this.bio = bio;
    }
}

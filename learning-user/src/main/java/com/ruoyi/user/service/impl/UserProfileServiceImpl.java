package com.ruoyi.user.service.impl;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ruoyi.common.config.OssConfig;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.user.domain.UserProfile;
import com.ruoyi.user.mapper.UserProfileMapper;
import com.ruoyi.user.service.IUserProfileService;

/**
 * 用户资料 服务层处理
 *
 * 几条规矩：
 *   1) 只能改自己的资料：userId 由 API 上下文传入，SQL 里 user_id 进 where，不接受前端传；
 *   2) 头像只认上传接口返回的地址（与短视频封面同一口径）——
 *      允许任意外链等于让所有人往资料里塞外部图片，图床盗链、http 混合内容、以后审核都管不到；
 *   3) 昵称/头像/性别落 sys_user（后台用户管理看到的是同一份数据），简介落 learning_user_profile，
 *      两条语句在同一事务里，用户不存在时整笔回滚。
 *
 * @author edu
 */
@Service
public class UserProfileServiceImpl implements IUserProfileService
{
    @Autowired
    private UserProfileMapper userProfileMapper;

    @Override
    public Map<String, Object> profile(Long userId)
    {
        checkUser(userId);
        UserProfile profile = userProfileMapper.selectProfile(userId);
        if (profile == null)
        {
            throw new ServiceException("用户不存在或已被删除");
        }
        return toResult(profile);
    }

    @Override
    @Transactional
    public Map<String, Object> updateProfile(Long userId, String userName, String avatar, String sex, String bio)
    {
        checkUser(userId);
        String name = checkUserName(userName);
        String sexValue = checkSex(sex);
        String avatarUrl = checkAvatar(avatar);
        String bioText = checkBio(bio);

        // 昵称/头像/性别：0 行 = 用户不存在或已被删除，整笔事务回滚（简介也不会写）
        if (userProfileMapper.updateUserBasic(userId, name, avatarUrl, sexValue) == 0)
        {
            throw new ServiceException("用户不存在或已被删除");
        }
        // 简介：行不存在就建（老用户懒创建）
        userProfileMapper.upsertBio(userId, bioText);

        return profile(userId);
    }

    private Map<String, Object> toResult(UserProfile profile)
    {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("userId", profile.getUserId());
        result.put("userName", profile.getUserName());
        // 头像没设置时统一给 null（老数据里 sys_user.avatar 是空串，前端不必区分 "" 和 null）
        result.put("avatar", StringUtils.isEmpty(profile.getAvatar()) ? null : profile.getAvatar());
        result.put("sex", profile.getSex());
        result.put("sexLabel", UserProfile.sexLabelOf(profile.getSex()));
        // 简介同头像：空串统一成 null
        result.put("bio", StringUtils.isEmpty(profile.getBio()) ? null : profile.getBio());
        return result;
    }

    private void checkUser(Long userId)
    {
        if (userId == null)
        {
            throw new ServiceException("请先登录");
        }
    }

    /**
     * 昵称：去首尾空格后不能为空、不超过 20 字
     *
     * 单字昵称允许（"张"是合法的），所以下限是 1。
     * 长度与 sys_user.user_name 的 varchar(30) 对齐并留余量，服务层先拦能给明确文案。
     */
    private String checkUserName(String userName)
    {
        String name = StringUtils.trim(userName);
        if (StringUtils.isEmpty(name))
        {
            throw new ServiceException("昵称不能为空");
        }
        if (name.length() > UserProfile.MAX_USER_NAME_LENGTH)
        {
            throw new ServiceException("昵称不能超过" + UserProfile.MAX_USER_NAME_LENGTH + "个字");
        }
        return name;
    }

    /**
     * 性别：只认 0/1/2（不公开=2）
     */
    private String checkSex(String sex)
    {
        String value = StringUtils.trim(sex);
        if (StringUtils.isEmpty(value))
        {
            throw new ServiceException("性别不能为空");
        }
        if (!UserProfile.isValidSex(value))
        {
            throw new ServiceException("性别不合法");
        }
        return value;
    }

    /**
     * 头像：空 = 清空；否则必须是上传接口返回的地址
     *
     * 开了 OSS 只认 OSS 前缀（上传接口返回的就是这个）；
     * 没开 OSS 只认 /profile/ 路径（本地存储）。
     */
    private String checkAvatar(String avatar)
    {
        String url = StringUtils.trim(avatar);
        if (StringUtils.isEmpty(url))
        {
            return null;
        }
        if (url.length() > UserProfile.MAX_AVATAR_LENGTH)
        {
            throw new ServiceException("头像地址过长");
        }
        boolean allowed = OssConfig.isEnabled()
                ? url.startsWith(OssConfig.resolveUrlPrefix() + "/")
                : url.contains("/profile/");
        if (!allowed)
        {
            throw new ServiceException("头像地址必须是上传接口返回的地址，请重新上传");
        }
        return url;
    }

    /**
     * 简介：允许为空（清空），不能超过 200 字
     */
    private String checkBio(String bio)
    {
        String text = StringUtils.trim(bio);
        if (StringUtils.isEmpty(text))
        {
            return null;
        }
        if (text.length() > UserProfile.MAX_BIO_LENGTH)
        {
            throw new ServiceException("个人简介不能超过" + UserProfile.MAX_BIO_LENGTH + "个字");
        }
        return text;
    }
}

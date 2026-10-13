package com.ruoyi.framework.shiro.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import com.ruoyi.common.constant.Constants;
import com.ruoyi.common.constant.ShiroConstants;
import com.ruoyi.common.constant.UserConstants;
import com.ruoyi.common.core.domain.entity.SysRole;
import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.common.utils.DateUtils;
import com.ruoyi.common.utils.MessageUtils;
import com.ruoyi.common.utils.ServletUtils;
import com.ruoyi.common.utils.ShiroUtils;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.framework.manager.AsyncManager;
import com.ruoyi.framework.manager.factory.AsyncFactory;
import com.ruoyi.system.service.ISysConfigService;
import com.ruoyi.system.service.ISysRoleService;
import com.ruoyi.system.service.ISysUserService;

/**
 * 注册校验方法（账号为手机号）
 *
 * @author ruoyi
 */
@Component
public class SysRegisterService
{
    /**
     * 注册用户默认角色的配置项，值为角色的 role_key
     */
    public static final String DEFAULT_ROLE_CONFIG_KEY = "sys.account.defaultRole";

    /**
     * 配置项未配置时回退的默认角色
     */
    public static final String DEFAULT_ROLE_KEY = "common";

    @Autowired
    private ISysUserService userService;

    @Autowired
    private ISysRoleService roleService;

    @Autowired
    private ISysConfigService configService;

    @Autowired
    private SysPasswordService passwordService;

    /**
     * 注册
     *
     * @param user 注册信息，账号取 phonenumber（原生注册页提交的是 loginName）
     * @return 注册成功返回空字符串，失败返回错误消息
     */
    @Transactional
    public String register(SysUser user)
    {
        // 接口传 phonenumber，原生注册页传 loginName，这里统一收敛为手机号
        String phone = StringUtils.trim(
                StringUtils.isNotEmpty(user.getPhonenumber()) ? user.getPhonenumber() : user.getLoginName());
        String password = user.getPassword();

        if (ShiroConstants.CAPTCHA_ERROR.equals(ServletUtils.getRequest().getAttribute(ShiroConstants.CURRENT_CAPTCHA)))
        {
            return "验证码错误";
        }
        else if (StringUtils.isEmpty(phone))
        {
            return MessageUtils.message("user.mobile.phone.number.empty");
        }
        else if (!phone.matches(UserConstants.MOBILE_PHONE_NUMBER_PATTERN))
        {
            return MessageUtils.message("user.mobile.phone.number.not.valid");
        }
        else if (StringUtils.isEmpty(password))
        {
            return MessageUtils.message("not.null");
        }
        else if (password.length() < UserConstants.PASSWORD_MIN_LENGTH
                || password.length() > UserConstants.PASSWORD_MAX_LENGTH)
        {
            return MessageUtils.message("user.password.not.match");
        }

        SysUser probe = new SysUser();
        probe.setPhonenumber(phone);
        if (!userService.checkPhoneUnique(probe))
        {
            return MessageUtils.message("user.phonenumber.registered");
        }
        probe.setLoginName(phone);
        if (!userService.checkLoginNameUnique(probe))
        {
            return MessageUtils.message("user.phonenumber.registered");
        }

        // 默认角色在写库前解析，避免产生没有角色的半成品用户
        SysRole role = selectDefaultRole();
        if (StringUtils.isNull(role))
        {
            return MessageUtils.message("user.register.role.missing");
        }

        user.setLoginName(phone);
        user.setPhonenumber(phone);
        user.setUserName(phone);
        user.setUserType(UserConstants.REGISTER_USER_TYPE);
        user.setStatus(UserConstants.NORMAL);
        user.setPwdUpdateDate(DateUtils.getNowDate());
        user.setSalt(ShiroUtils.randomSalt());
        user.setPassword(passwordService.encryptPassword(phone, password, user.getSalt()));

        if (!userService.registerUser(user))
        {
            return "注册失败,请联系系统管理人员";
        }
        userService.insertUserAuth(user.getUserId(), new Long[] { role.getRoleId() });

        AsyncManager.me().execute(AsyncFactory.recordLogininfor(phone, Constants.REGISTER, MessageUtils.message("user.register.success")));
        return "";
    }

    /**
     * 查询注册用户的默认角色
     *
     * @return 可用（未删除且未停用）的角色，取不到时返回 null
     */
    private SysRole selectDefaultRole()
    {
        String roleKey = StringUtils.trim(configService.selectConfigByKey(DEFAULT_ROLE_CONFIG_KEY));
        if (StringUtils.isEmpty(roleKey))
        {
            roleKey = DEFAULT_ROLE_KEY;
        }
        SysRole role = roleService.selectRoleByKey(roleKey);
        if (StringUtils.isNull(role) || !UserConstants.ROLE_NORMAL.equals(role.getStatus()))
        {
            return null;
        }
        return role;
    }
}

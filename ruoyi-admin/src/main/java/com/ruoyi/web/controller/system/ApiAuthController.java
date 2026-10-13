package com.ruoyi.web.controller.system;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.authc.AuthenticationException;
import org.apache.shiro.authc.UsernamePasswordToken;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.ruoyi.common.annotation.Anonymous;
import com.ruoyi.common.constant.UserConstants;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.domain.entity.SysRole;
import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.common.utils.MessageUtils;
import com.ruoyi.common.utils.ShiroUtils;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.framework.shiro.service.SysRegisterService;
import com.ruoyi.system.service.ISysConfigService;

/**
 * 手机号登录注册接口
 *
 * @author ruoyi
 */
@RestController
@RequestMapping("/api")
public class ApiAuthController extends BaseController
{
    /**
     * 是否开启用户注册
     */
    private static final String REGISTER_CONFIG_KEY = "sys.account.registerUser";

    @Autowired
    private ISysConfigService configService;

    @Autowired
    private SysRegisterService registerService;

    /**
     * 手机号密码登录
     *
     * @param phone 手机号（登录账号）
     * @param password 密码
     */
    @Anonymous
    @PostMapping("/login")
    public AjaxResult login(String phone, String password)
    {
        phone = StringUtils.trim(phone);
        if (StringUtils.isEmpty(phone))
        {
            return error(MessageUtils.message("user.mobile.phone.number.empty"));
        }
        if (!phone.matches(UserConstants.MOBILE_PHONE_NUMBER_PATTERN))
        {
            return error(MessageUtils.message("user.mobile.phone.number.not.valid"));
        }

        UsernamePasswordToken token = new UsernamePasswordToken(phone, password, false);
        try
        {
            SecurityUtils.getSubject().login(token);
        }
        catch (AuthenticationException e)
        {
            String msg = MessageUtils.message("user.not.exists");
            if (StringUtils.isNotEmpty(e.getMessage()))
            {
                msg = e.getMessage();
            }
            return error(msg);
        }

        AjaxResult ajax = success();
        SysUser user = ShiroUtils.getSysUser();
        if (StringUtils.isNotNull(user))
        {
            ajax.put("userId", user.getUserId());
            ajax.put("phonenumber", user.getPhonenumber());
            ajax.put("userName", user.getUserName());
            ajax.put("roles", roleKeys(user));
        }
        return ajax;
    }

    /**
     * 手机号注册
     *
     * @param phone 手机号（登录账号）
     * @param password 密码
     * @param confirmPassword 确认密码
     */
    @Anonymous
    @PostMapping("/register")
    public AjaxResult register(String phone, String password, String confirmPassword)
    {
        if (!("true".equals(configService.selectConfigByKey(REGISTER_CONFIG_KEY))))
        {
            return error("当前系统没有开启注册功能！");
        }
        if (!StringUtils.equals(password, confirmPassword))
        {
            return error(MessageUtils.message("user.password.confirm.not.match"));
        }

        SysUser user = new SysUser();
        user.setPhonenumber(StringUtils.trim(phone));
        user.setPassword(password);

        String msg = registerService.register(user);
        return StringUtils.isEmpty(msg) ? success() : error(msg);
    }

    /**
     * 手机号密码登录（JSON 请求体）
     *
     * @param body 请求体，字段同表单：phone、password
     */
    @Anonymous
    @PostMapping(value = "/login", consumes = MediaType.APPLICATION_JSON_VALUE)
    public AjaxResult loginJson(@RequestBody Map<String, String> body)
    {
        return login(body.get("phone"), body.get("password"));
    }

    /**
     * 手机号注册（JSON 请求体）
     *
     * @param body 请求体，字段同表单：phone、password、confirmPassword
     */
    @Anonymous
    @PostMapping(value = "/register", consumes = MediaType.APPLICATION_JSON_VALUE)
    public AjaxResult registerJson(@RequestBody Map<String, String> body)
    {
        return register(body.get("phone"), body.get("password"), body.get("confirmPassword"));
    }

    /**
     * 用户角色标识集合
     *
     * @param user 用户信息
     */
    private List<String> roleKeys(SysUser user)
    {
        List<String> keys = new ArrayList<>();
        if (user.getRoles() != null)
        {
            for (SysRole role : user.getRoles())
            {
                keys.add(role.getRoleKey());
            }
        }
        return keys;
    }
}

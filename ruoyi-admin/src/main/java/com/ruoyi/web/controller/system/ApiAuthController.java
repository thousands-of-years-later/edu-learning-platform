package com.ruoyi.web.controller.system;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.authc.AuthenticationException;
import org.apache.shiro.authc.UsernamePasswordToken;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.ruoyi.api.domain.ApiToken;
import com.ruoyi.api.service.IApiTokenService;
import com.ruoyi.api.utils.ApiTokenUtils;
import com.ruoyi.common.annotation.Anonymous;
import com.ruoyi.common.constant.UserConstants;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.domain.entity.SysRole;
import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.common.exception.base.BaseException;
import com.ruoyi.common.utils.MessageUtils;
import com.ruoyi.common.utils.ShiroUtils;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.framework.shiro.service.SysLoginService;
import com.ruoyi.framework.shiro.service.SysRegisterService;
import com.ruoyi.system.service.ISysConfigService;

/**
 * 手机号登录注册接口
 *
 * 两套出口并存，老客户端不用改：
 *   老的 /api/login、/api/register：走 Shiro 会话，登录后靠 Cookie（JSESSIONID）鉴权；
 *   新的 /api/v1/login、/api/v1/register、/api/v1/logout：不建会话，返回 Token，
 *   客户端后续请求带 Authorization: Bearer &lt;token&gt;（校验见 learning-api 的 ApiTokenFilter）。
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
     * 登录校验服务（与后台登录、老的 /api/login 用的是同一个），
     * v1 登录复用它，就不必再抄一遍"密码重试锁定 / IP 黑名单 / 账号状态"这些安全校验
     */
    @Autowired
    private SysLoginService loginService;

    /**
     * Token 服务（签发 / 校验 / 吊销，learning-api 模块）
     */
    @Autowired
    private IApiTokenService apiTokenService;

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
     * 手机号密码登录（v1：签发 Token，不建 Shiro 会话）
     *
     * 与老的 /api/login 的区别：老接口调 SecurityUtils.getSubject().login()，
     * 服务端会建一个后台会话、下发 JSESSIONID；小程序不需要会话，
     * 它只要一个能长期带着走的凭证，所以这里直接调登录校验服务，
     * 校验通过后签发 Token（存 learning_api_token 表）。
     *
     * @param phone 手机号（登录账号）
     * @param password 密码
     * @return {token, expireTime, userId, phonenumber, userName, roles}
     */
    @Anonymous
    @PostMapping("/v1/login")
    public AjaxResult v1Login(String phone, String password)
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

        SysUser user;
        try
        {
            // 复用若依的登录校验：密码错误重试锁定、IP 黑名单、账号停用/删除都在里面，
            // 不自己再写一套（两套校验迟早会不一致）
            user = loginService.login(phone, password);
        }
        catch (BaseException e)
        {
            // 用户不存在、密码错误、账号停用等业务异常的 message 已经是中文提示
            return error(e.getMessage());
        }

        ApiToken apiToken = apiTokenService.createToken(user.getUserId(), ShiroUtils.getIp());
        AjaxResult ajax = success();
        ajax.put("token", apiToken.getToken());
        // 过期时间一并返回，客户端可以提前续登，而不是等收到 401 才发现过期
        ajax.put("expireTime", apiToken.getExpireTime());
        ajax.put("userId", user.getUserId());
        ajax.put("phonenumber", user.getPhonenumber());
        ajax.put("userName", user.getUserName());
        ajax.put("roles", roleKeys(user));
        return ajax;
    }

    /**
     * 手机号密码登录 v1（JSON 请求体）
     *
     * @param body 请求体，字段同表单：phone、password
     */
    @Anonymous
    @PostMapping(value = "/v1/login", consumes = MediaType.APPLICATION_JSON_VALUE)
    public AjaxResult v1LoginJson(@RequestBody Map<String, String> body)
    {
        return v1Login(body.get("phone"), body.get("password"));
    }

    /**
     * 手机号注册（v1）
     *
     * 注册成功【不】顺手发 Token：发 Token 属于登录的职责，
     * 客户端注册完调一次 /api/v1/login 换 Token，语义更清楚。
     *
     * @param phone 手机号（登录账号）
     * @param password 密码
     * @param confirmPassword 确认密码
     */
    @Anonymous
    @PostMapping("/v1/register")
    public AjaxResult v1Register(String phone, String password, String confirmPassword)
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
     * 手机号注册 v1（JSON 请求体）
     *
     * @param body 请求体，字段同表单：phone、password、confirmPassword
     */
    @Anonymous
    @PostMapping(value = "/v1/register", consumes = MediaType.APPLICATION_JSON_VALUE)
    public AjaxResult v1RegisterJson(@RequestBody Map<String, String> body)
    {
        return v1Register(body.get("phone"), body.get("password"), body.get("confirmPassword"));
    }

    /**
     * 退出登录（v1：吊销当前 Token）
     *
     * 故意不校验 Token 是否还有效：Token 已过期时也该能"退出成功"，
     * 而且只删自己这一个 Token（同一账号在别的设备上的登录不受影响），
     * 删不到也不报错 —— 接口做成幂等的，客户端不用处理"退不出去"的状态。
     *
     * @param request 请求（Token 从中取）
     */
    @Anonymous
    @PostMapping("/v1/logout")
    public AjaxResult v1Logout(HttpServletRequest request)
    {
        apiTokenService.revoke(ApiTokenUtils.resolveToken(request));
        return success();
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

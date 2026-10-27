package com.ruoyi.web.controller.api;

import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.ruoyi.common.constant.UserConstants;
import com.ruoyi.common.core.api.ApiContext;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.common.utils.MessageUtils;
import com.ruoyi.common.utils.ShiroUtils;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.framework.shiro.service.SysPasswordService;
import com.ruoyi.system.service.ISysUserService;
import com.ruoyi.web.service.UserPasswordChangeService;

/**
 * 修改密码 接口 v1（Token 认证，给小程序 / iOS / 安卓用）
 *
 * 只改当前登录用户自己的密码：userId 取 ApiContext，绝不接受前端传入。
 * 管理员继续用后台原生的「个人中心 → 修改密码」和「用户管理 → 重置密码」，这里不做管理员接口。
 *
 * 出口在 /api/v1/** 下：ShiroConfig 里整体放行，鉴权由 ApiTokenFilter 完成，
 * 所以这里【不加】@Anonymous 注解（标了反而会被误读成这个接口不需要登录）。
 * 同样也不加 @Log：LogAspect 取的是 Shiro 会话身份，Token 请求没有后台登录态。
 *
 * 密码算法完全复用原生 SysPasswordService（MD5(loginName + password + salt)），
 * 并且改密成功后吊销该账号全部 Token（含当前设备），客户端需要重新登录。
 *
 * @author edu
 */
@RestController
@RequestMapping("/api/v1/user")
public class ApiV1UserPasswordController extends BaseController
{
    @Autowired
    private ISysUserService userService;

    @Autowired
    private SysPasswordService passwordService;

    @Autowired
    private UserPasswordChangeService userPasswordChangeService;

    /**
     * 修改当前用户密码（表单提交）
     *
     * @param oldPassword     旧密码
     * @param newPassword     新密码（5~20 位）
     * @param confirmPassword 确认新密码
     * @return 成功：{code:0, msg:"密码修改成功，请重新登录", reLoginRequired:true}
     */
    @PostMapping("/password")
    public AjaxResult changePassword(String oldPassword, String newPassword, String confirmPassword)
    {
        Long userId = ApiContext.getUserId();
        if (userId == null)
        {
            return error("登录状态已失效，请重新登录");
        }
        if (StringUtils.isEmpty(oldPassword))
        {
            return error("旧密码不能为空");
        }
        if (StringUtils.isEmpty(newPassword))
        {
            return error("新密码不能为空");
        }
        if (StringUtils.isEmpty(confirmPassword))
        {
            return error("确认密码不能为空");
        }
        if (newPassword.length() < UserConstants.PASSWORD_MIN_LENGTH
                || newPassword.length() > UserConstants.PASSWORD_MAX_LENGTH)
        {
            return error(MessageUtils.message("user.password.length.not.valid"));
        }
        if (!StringUtils.equals(newPassword, confirmPassword))
        {
            return error(MessageUtils.message("user.password.confirm.not.match"));
        }

        // 重新查库拿 password / salt：ApiTokenFilter 只把 userId 放进上下文
        SysUser user = userService.selectUserById(userId);
        if (user == null || !UserConstants.NORMAL.equals(user.getStatus())
                || !UserConstants.NORMAL.equals(user.getDelFlag()))
        {
            return error("登录状态已失效，请重新登录");
        }
        if (!passwordService.matches(user, oldPassword))
        {
            return error("修改密码失败，旧密码错误");
        }
        if (passwordService.matches(user, newPassword))
        {
            return error("新密码不能与旧密码相同");
        }

        user.setSalt(ShiroUtils.randomSalt());
        user.setPassword(passwordService.encryptPassword(user.getLoginName(), newPassword, user.getSalt()));
        if (!userPasswordChangeService.resetUserPwdAndRevokeTokens(user))
        {
            return error("修改密码异常，请联系管理员");
        }

        // 密码已落库：清掉该账号的登录失败计数，避免旧密码输错导致的锁定状态延续
        passwordService.clearLoginRecordCache(user.getLoginName());

        AjaxResult ajax = success("密码修改成功，请重新登录");
        // 全部 Token（包括当前这个）已在事务里删除，客户端收到后必须清掉本地凭证并跳登录页
        ajax.put("reLoginRequired", true);
        return ajax;
    }

    /**
     * 修改当前用户密码（JSON 请求体）
     *
     * @param body 请求体，字段同表单：oldPassword、newPassword、confirmPassword
     */
    @PostMapping(value = "/password", consumes = MediaType.APPLICATION_JSON_VALUE)
    public AjaxResult changePasswordJson(@RequestBody Map<String, String> body)
    {
        if (body == null)
        {
            return error("请求体不能为空");
        }
        return changePassword(body.get("oldPassword"), body.get("newPassword"), body.get("confirmPassword"));
    }
}

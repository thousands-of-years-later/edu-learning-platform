package com.ruoyi.user.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.user.domain.Agreement;
import com.ruoyi.user.service.IAgreementService;

/**
 * 平台协议 接口 v1（Token 认证，给 App / 小程序用）
 *
 * GET /api/v1/agreement/list       三种协议的列表（不含正文）
 * GET /api/v1/agreement/{type}     按类型取单篇协议（含正文 HTML）
 *
 * 只返回 status='0' 且未删除的记录；停用/删除与不存在一样返回错误，避免探测。
 * 后台维护入口在 /user/agreement（AgreementController）。
 *
 * 这两个接口是【免登录】的：ApiTokenFilter 已把 /api/v1/agreement/** 加进前缀白名单，
 * 登录页/注册页在尚未拿到 Token 时也能展示协议；带 Token 访问同样可以。
 * 之所以不标 @Anonymous：该注解只影响 Shiro 的 anon 链（/api/v1/** 本来就整体放行），
 * 真正的 Token 校验在 ApiTokenFilter 里，标了反而误导。
 *
 * 错误一律直接返回 AjaxResult：普通 GET 抛 ServiceException 会被全局异常处理器
 * 导向 HTML 错误页，客户端解析 JSON 会直接失败。
 *
 * @author edu
 */
@RestController
@RequestMapping("/api/v1/agreement")
public class ApiV1AgreementController extends BaseController
{
    @Autowired
    private IAgreementService agreementService;

    /**
     * 协议列表（三种类型，按 order_num 升序，不分页）
     *
     * @return {code:0, rows:[{agreementId, agreementType, typeLabel, title, orderNum, status, updateTime}], total}
     *         列表不返回 content，正文按类型单独查，避免一次把三篇长文全带出来
     */
    @GetMapping("/list")
    public TableDataInfo list()
    {
        // 固定三种协议，是有限集合，不调用 startPage()
        return getDataTable(agreementService.selectEnabledAgreementList());
    }

    /**
     * 按类型查询协议详情（含正文 HTML）
     *
     * 路径参数用 String 接：非法的类型也要返回 JSON，不能让它走到参数绑定异常。
     *
     * @param agreementType user_agreement（用户协议）/ privacy_policy（隐私政策）/ about_us（关于我们）
     * @return {code:0, data:{agreementId, agreementType, typeLabel, title, content, orderNum, status, ...}}
     *         类型非法：{code:500, msg:"协议类型不合法..."}
     *         不存在/已停用：{code:500, msg:"协议不存在或已停用"}
     */
    @GetMapping("/{agreementType}")
    public AjaxResult detail(@PathVariable("agreementType") String agreementType)
    {
        String type = StringUtils.trim(agreementType);
        if (!Agreement.isAllowedType(type))
        {
            return AjaxResult.error("协议类型不合法（仅支持 user_agreement/privacy_policy/about_us）");
        }
        Agreement agreement = agreementService.selectEnabledAgreementByType(type);
        if (StringUtils.isNull(agreement))
        {
            return AjaxResult.error("协议不存在或已停用");
        }
        return AjaxResult.success(agreement);
    }
}

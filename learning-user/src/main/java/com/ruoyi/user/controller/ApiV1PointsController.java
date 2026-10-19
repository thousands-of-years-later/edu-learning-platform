package com.ruoyi.user.controller;

import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.ruoyi.common.core.api.ApiContext;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.text.Convert;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.user.service.IPointsService;

/**
 * 积分 / 权益 接口 v1（Token 认证，给小程序用）
 *
 * 对应设计稿：
 *   08_任务中心  → /account（"当前无免广告权益"那块）+ /exchange/tiers（兑换列表）+ /exchange
 *   19_积分规则  → /exchange/tiers（"积分兑换规则"三段）
 *
 * 积分余额与签到共用同一个账户（见 learning-user 的 learning_points_account）：
 * 签到负责加、这里负责减；免广告权益就挂在账户行的 ad_free_expire_time 上。
 *
 * 用户ID一律取 ApiContext，绝不接受前端传入。
 *
 * @author edu
 */
@RestController
@RequestMapping("/api/v1/points")
public class ApiV1PointsController extends BaseController
{
    @Autowired
    private IPointsService pointsService;

    /**
     * 我的积分账户（余额 + 免广告权益状态）
     *
     * adFree 由服务端按到期时间算好，客户端不要用本地时间自己判断（手机时间可以随便改）；
     * 「免广告权益剩余 X 小时」也直接用 adFreeRemainMinutes，别拿 adFreeExpireTime 本地减。
     *
     * @return {balance, adFree, adFreeExpireTime, adFreeRemainMinutes}
     *         （无权益时 adFreeExpireTime 为 null、adFreeRemainMinutes 为 0）
     */
    @GetMapping("/account")
    public AjaxResult account()
    {
        AjaxResult ajax = AjaxResult.success();
        ajax.putAll(pointsService.account(ApiContext.getUserId()));
        return ajax;
    }

    /**
     * 免广告兑换档位
     *
     * @return {tiers:[{tier:1, cost:10, minutes:30, label:"30 分钟"}, ...]}
     */
    @GetMapping("/exchange/tiers")
    public AjaxResult exchangeTiers()
    {
        AjaxResult ajax = AjaxResult.success();
        ajax.put("tiers", pointsService.exchangeTiers());
        return ajax;
    }

    /**
     * 兑换免广告（权益叠加：没过期就从原到期时间往后加，过期了从此刻开始算）
     *
     * @param tier 档位（1 = 10 积分/30 分钟，2 = 30 积分/2 小时，3 = 100 积分/24 小时）
     * @return {balance:兑换后余额, adFree:true, adFreeExpireTime:新的到期时间,
     *          adFreeRemainMinutes:兑换后的剩余分钟（含叠加）, cost, label}
     *         积分不足 → code=500, msg="积分不足"（余额与权益都不变）
     */
    @PostMapping("/exchange")
    public AjaxResult exchange(Integer tier)
    {
        AjaxResult ajax = AjaxResult.success("兑换成功");
        ajax.putAll(pointsService.exchange(ApiContext.getUserId(), tier));
        return ajax;
    }

    /**
     * 兑换免广告（JSON 请求体）
     *
     * @param body 请求体，字段同表单：tier
     */
    @PostMapping(value = "/exchange", consumes = MediaType.APPLICATION_JSON_VALUE)
    public AjaxResult exchangeJson(@RequestBody Map<String, String> body)
    {
        String rawTier = body.get("tier");
        Integer tier = Convert.toInt(rawTier);
        // Convert.toInt 对「有值但转不动」是静默返回 null 的，null 会走到"档位不能为空"，
        // 但那句文案对 "abc" 这种输入不够准确，这里先挡掉（与表单出口保持一致）
        if (tier == null && StringUtils.isNotEmpty(rawTier))
        {
            return AjaxResult.error("兑换档位不合法");
        }
        return exchange(tier);
    }
}

package com.ruoyi.user.controller;

import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.ruoyi.common.core.api.ApiContext;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.text.Convert;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.user.domain.PointsLog;
import com.ruoyi.user.domain.PointsSource;
import com.ruoyi.user.service.IPointsRewardService;
import com.ruoyi.user.service.IPointsService;

/**
 * 积分 / 权益 接口 v1（Token 认证，给 App / 小程序用）
 *
 * 现有入口：
 *   · GET  /api/v1/points/account         我的积分账户（余额 + 免广告权益）
 *   · GET  /api/v1/points/exchange/tiers  免广告兑换档位（已下线，前端已无调用；保留用于历史数据/到期逻辑）
 *   · POST /api/v1/points/exchange        兑换免广告（已下线，前端已无调用；保留服务层）
 *   · POST /api/v1/points/watch           观看课程小节 / 帖子，上报秒数（满 30 秒 +5）
 *   · POST /api/v1/points/ad/reward       看完广告领 10 分（每天最多 3 条）
 *
 * 用户ID一律取 ApiContext（ApiTokenFilter 校验 Token 后写入），绝不接受前端传入。
 * 去重、每日上限、并发抢占都在 IPointsRewardService 里处理。
 *
 * @author edu
 */
@RestController
@RequestMapping("/api/v1/points")
public class ApiV1PointsController extends BaseController
{
    @Autowired
    private IPointsService pointsService;

    @Autowired
    private IPointsRewardService pointsRewardService;

    /**
     * 我的积分账户（余额 + 免广告权益状态）
     *
     * adFree 由服务端按到期时间算好，客户端不要用本地时间自己判断（手机时间可以随便改）；
     * 「免广告权益剩余 X 小时」也直接用 adFreeRemainMinutes，别拿 adFreeExpireTime 本地减。
     *
     * @return {balance, adFree, adFreeExpireTime, adFreeRemainMinutes}
     */
    @GetMapping("/account")
    public AjaxResult account()
    {
        AjaxResult ajax = AjaxResult.success();
        ajax.putAll(pointsService.account(ApiContext.getUserId()));
        return ajax;
    }

    /**
     * 商城积分明细（分页）
     *
     * @param pageNum  页码，默认 1
     * @param pageSize 每页条数，默认 20，最大 50
     * @param type     积分类型编码，可选；非法编码直接报错
     * @return {total, rows:[{logId, changeAmount, balance, type, sourceLabel, remark, refId, createTime}]}
     */
    @GetMapping("/logs")
    public AjaxResult logs(Integer pageNum, Integer pageSize, String type)
    {
        int page = pageNum == null ? 1 : pageNum;
        int size = pageSize == null ? 20 : pageSize;
        if (page < 1)
        {
            return AjaxResult.error("页码不合法");
        }
        if (size < 1 || size > 50)
        {
            return AjaxResult.error("每页条数不合法（1~50）");
        }
        String source = StringUtils.trim(type);
        if (StringUtils.isNotEmpty(source) && !PointsSource.isKnown(source))
        {
            return AjaxResult.error("积分类型不合法");
        }

        PageHelper.startPage(page, size);
        List<PointsLog> list = pointsService.selectLogsByUser(ApiContext.getUserId(), source);
        PageInfo<PointsLog> pageInfo = new PageInfo<>(list);

        AjaxResult ajax = AjaxResult.success();
        ajax.put("total", pageInfo.getTotal());
        ajax.put("rows", list);
        return ajax;
    }

    /**
     * 免广告兑换档位（已下线）
     *
     * 前端已彻底删除调用；接口与服务层保留，历史流水展示和积分账户到期逻辑仍在使用。
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
     * 兑换免广告（已下线）
     *
     * 前端已彻底删除调用；接口与服务层保留，历史流水与积分账户到期逻辑仍在使用。
     *
     * @param tier 档位（1 = 10 积分/30 分钟，2 = 30 积分/2 小时，3 = 100 积分/24 小时）
     */
    @PostMapping("/exchange")
    public AjaxResult exchange(Integer tier)
    {
        AjaxResult ajax = AjaxResult.success("兑换成功");
        ajax.putAll(pointsService.exchange(ApiContext.getUserId(), tier));
        return ajax;
    }

    /**
     * 兑换免广告（JSON 请求体，已下线）
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

    /**
     * 上报观看时长（表单）
     *
     * @param targetType post（帖子）/ courseVideo（课程小节）
     * @param targetId   帖子ID / 课程小节ID
     * @param seconds    本次新增观看秒数（1~3600）
     * @return {awardedPoints, todayContentPoints, todayContentLimit, watchedSeconds}
     */
    @PostMapping("/watch")
    public AjaxResult watch(String targetType, Long targetId, Integer seconds)
    {
        AjaxResult ajax = AjaxResult.success();
        ajax.putAll(pointsRewardService.watch(ApiContext.getUserId(), targetType, targetId, seconds));
        return ajax;
    }

    /**
     * 上报观看时长（JSON）
     *
     * @param body 字段同表单：targetType、targetId、seconds
     */
    @PostMapping(value = "/watch", consumes = MediaType.APPLICATION_JSON_VALUE)
    public AjaxResult watchJson(@RequestBody Map<String, String> body)
    {
        return watch(body.get("targetType"), Convert.toLong(body.get("targetId")),
                Convert.toInt(body.get("seconds")));
    }

    /**
     * 看完广告领积分（表单）
     *
     * @param adId    广告唯一标识（最长 64）
     * @param seconds 本次观看秒数（至少 15 秒）
     * @return {awardedPoints, todayAdCount, todayAdLimit}
     */
    @PostMapping("/ad/reward")
    public AjaxResult adReward(String adId, Integer seconds)
    {
        AjaxResult ajax = AjaxResult.success();
        ajax.putAll(pointsRewardService.ad(ApiContext.getUserId(), adId, seconds));
        return ajax;
    }

    /**
     * 看完广告领积分（JSON）
     *
     * @param body 字段同表单：adId、seconds
     */
    @PostMapping(value = "/ad/reward", consumes = MediaType.APPLICATION_JSON_VALUE)
    public AjaxResult adRewardJson(@RequestBody Map<String, String> body)
    {
        return adReward(body.get("adId"), Convert.toInt(body.get("seconds")));
    }
}

package com.ruoyi.user.controller;

import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.ruoyi.common.core.api.ApiContext;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.text.Convert;
import com.ruoyi.user.service.IPointsRewardService;

/**
 * 积分获取 接口 v1（Token 认证，给 App / 小程序用）
 *
 * 两个入口：
 *   · POST /api/v1/points/watch      观看课程小节 / 帖子，上报本次新增秒数，满 30 秒发 5 分；
 *   · POST /api/v1/points/ad/reward  看完广告领 10 分（每天最多 3 条）。
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
    private IPointsRewardService pointsRewardService;

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

package com.ruoyi.user.controller;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.ruoyi.common.core.api.ApiContext;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.user.domain.CheckinRecord;
import com.ruoyi.user.service.ICheckinService;

/**
 * 签到 / 积分 接口 v1（Token 认证，给小程序用）
 *
 * 对应设计稿三张图：
 *   08_任务中心  → /status（我的积分、连续/累计天数、签到大按钮的"今天可得几分"）
 *   07_签到记录  → /calendar（月历）+ /records（最近签到）+ /status（三个统计卡）
 *   19_积分规则  → /rules（签到档位表；兑换部分属于另一个板块，不在本接口）
 *
 * 只有这一套入口（没有 /api/checkin 会话版）：签到是新功能，没有存量客户端。
 * 用户ID一律取 ApiContext（ApiTokenFilter 校验 Token 后写入），绝不接受前端传入；
 * 「今天」也由服务端按东八区算，客户端传什么日期都不算数。
 *
 * @author edu
 */
@RestController
@RequestMapping("/api/v1/checkin")
public class ApiV1CheckinController extends BaseController
{
    @Autowired
    private ICheckinService checkinService;

    /**
     * 每日签到
     *
     * @return {code, msg, points:本次得分, continuousDays:连续天数, totalDays:累计天数,
     *          balance:签到后余额, signDate:签到日期}
     *         今天已签过 → code=500, msg="今天已经签到过了"
     */
    @PostMapping("/sign")
    public AjaxResult sign()
    {
        AjaxResult ajax = AjaxResult.success("签到成功");
        ajax.putAll(checkinService.sign(ApiContext.getUserId()));
        return ajax;
    }

    /**
     * 签到状态（任务中心头部 + 签到大按钮）
     *
     * @return {signedToday:今天签没签, todayPoints:今天签到可得几分（已签为 0）,
     *          continuousDays:连续签到天数, totalDays:累计签到天数,
     *          balance:积分余额, today:服务器今天(yyyy-MM-dd)}
     */
    @GetMapping("/status")
    public AjaxResult status()
    {
        AjaxResult ajax = AjaxResult.success();
        ajax.putAll(checkinService.status(ApiContext.getUserId()));
        return ajax;
    }

    /**
     * 某月的签到日历（签到记录页的月历）
     *
     * @param year  年份（不传 = 今年）
     * @param month 月份（不传 = 本月）
     * @return {year, month, days:[几号...], today, signedToday}
     */
    @GetMapping("/calendar")
    public AjaxResult calendar(Integer year, Integer month)
    {
        AjaxResult ajax = AjaxResult.success();
        ajax.putAll(checkinService.calendar(ApiContext.getUserId(), year, month));
        return ajax;
    }

    /**
     * 我的签到记录（最近签到列表，分页）
     *
     * @return {total, rows:[{checkinId, signDate:"2026-10-09", points:15, continuousDays:6, createTime}]}
     *         （分页参数 pageNum / pageSize）
     */
    @GetMapping("/records")
    public TableDataInfo records()
    {
        startPage();
        List<CheckinRecord> list = checkinService.selectRecords(ApiContext.getUserId());
        return getDataTable(list);
    }

    /**
     * 签到积分规则（"积分规则"页的签到档位表）
     *
     * @return {tiers:[{day:1,points:5}...{day:7,points:20}], loopDays:7,
     *          dailyLimit:1, dailyTip, resetNote, loopNote}
     */
    @GetMapping("/rules")
    public AjaxResult rules()
    {
        AjaxResult ajax = AjaxResult.success();
        ajax.putAll(checkinService.rules());
        return ajax;
    }
}

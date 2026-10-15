package com.ruoyi.community.controller;

import java.util.List;
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
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.common.core.text.Convert;
import com.ruoyi.community.domain.Follow;
import com.ruoyi.community.service.IFollowService;

/**
 * 关注 接口 v1（Token 认证，给小程序用）
 *
 * 与 ApiFollowController（/api/follow）功能一一对应，
 * 差别只在【用户ID从哪来】：
 *   老的走 Shiro 会话：/api/** 默认要求登录，用户ID取 ShiroUtils.getUserId()；
 *   这里走 Token：/api/v1/** 在 ShiroConfig 里是 anon，Shiro 没有登录态，
 *   用户ID取自 ApiContext（由 ApiTokenFilter 校验 Token 后写入）。
 *
 * 两个入口同时存在：老客户端不用改，新客户端用 /api/v1。
 * 这里【不加】@Anonymous 注解 —— /api/v1/** 是整体放行的，真正的鉴权由 ApiTokenFilter 做，
 * 标上 @Anonymous 反而会让人误以为这个接口不需要登录。
 *
 * 用户ID一律取上下文，绝不接受前端传入。
 *
 * 关注/取关做成两个【幂等】接口（add / cancel），不做 toggle：
 * 客户端双击、网络重试、把同一条请求发两遍，结果都一样。
 * count / following / fans 的 userId 参数只是「查谁」，不是「我是谁」——「我是谁」永远来自 Token。
 *
 * 每个写接口都提供【表单】与【JSON】两个出口，靠 consumes 区分（与 ApiV1RatingController 同一写法）。
 *
 * @author edu
 */
@RestController
@RequestMapping("/api/v1/follow")
public class ApiV1FollowController extends BaseController
{
    @Autowired
    private IFollowService followService;

    /**
     * 关注某人（幂等：已关注时重复调用也返回成功）
     *
     * @param targetUserId 被关注用户ID
     * @return {active: true, fansCount: 对方操作后的真实粉丝数}
     */
    @PostMapping("/add")
    public AjaxResult add(Long targetUserId)
    {
        return toResult(followService.addFollow(ApiContext.getUserId(), targetUserId));
    }

    /**
     * 关注某人（JSON 请求体）
     *
     * @param body 请求体，字段同表单：targetUserId
     */
    @PostMapping(value = "/add", consumes = MediaType.APPLICATION_JSON_VALUE)
    public AjaxResult addJson(@RequestBody Map<String, String> body)
    {
        return add(Convert.toLong(body.get("targetUserId")));
    }

    /**
     * 取消关注（幂等：本来就没关注也返回成功）
     *
     * @param targetUserId 被取消关注的用户ID
     * @return {active: false, fansCount: 对方操作后的真实粉丝数}
     */
    @PostMapping("/cancel")
    public AjaxResult cancel(Long targetUserId)
    {
        return toResult(followService.cancelFollow(ApiContext.getUserId(), targetUserId));
    }

    /**
     * 取消关注（JSON 请求体）
     *
     * @param body 请求体，字段同表单：targetUserId
     */
    @PostMapping(value = "/cancel", consumes = MediaType.APPLICATION_JSON_VALUE)
    public AjaxResult cancelJson(@RequestBody Map<String, String> body)
    {
        return cancel(Convert.toLong(body.get("targetUserId")));
    }

    /**
     * 批量查询当前用户已关注的目标用户ID（供列表页一次性回填关注按钮状态）
     *
     * @param userIds 目标用户ID，逗号分隔（一次最多200个）
     * @return {ids: [已关注的用户ID]}
     */
    @GetMapping("/status")
    public AjaxResult status(String userIds)
    {
        Long[] ids = Convert.toLongArray(userIds);
        AjaxResult ajax = AjaxResult.success();
        ajax.put("ids", followService.selectFollowedUserIds(ApiContext.getUserId(), ids));
        return ajax;
    }

    /**
     * 查某用户的粉丝数 + 关注数
     *
     * @param userId 要查的用户ID（可选，缺省查自己）
     * @return {fansCount: 粉丝数, followCount: 关注数}
     */
    @GetMapping("/count")
    public AjaxResult count(Long userId)
    {
        Long queryUserId = userId == null ? ApiContext.getUserId() : userId;
        Map<String, Object> counts = followService.selectCounts(queryUserId);
        return AjaxResult.success()
                .put("fansCount", counts.get("fansCount"))
                .put("followCount", counts.get("followCount"));
    }

    /**
     * 我关注的人（分页）
     *
     * @param userId 要查的用户ID（可选，缺省查自己），只是「查谁的列表」，不是「我是谁」
     * @return {rows: [Follow], total}（分页参数 pageNum / pageSize）；
     *         rows 里的展示用户是 followUserId 那一位（userName / avatar 也是他的）
     */
    @GetMapping("/following")
    public TableDataInfo following(Long userId)
    {
        Long queryUserId = userId == null ? ApiContext.getUserId() : userId;
        // 校验必须在 startPage() 之前：PageHelper 的分页参数挂在 ThreadLocal 上，
        // 会被它之后的第一条查询消费，而校验里就有查询（见 IFollowService.checkQueryUser）
        followService.checkQueryUser(queryUserId);
        startPage();
        List<Follow> list = followService.selectFollowingList(queryUserId);
        return getDataTable(list);
    }

    /**
     * 我的粉丝（分页）
     *
     * @param userId 要查的用户ID（可选，缺省查自己）
     * @return {rows: [Follow], total}；rows 里的展示用户是 userId 那一位（userName / avatar 也是他的）
     */
    @GetMapping("/fans")
    public TableDataInfo fans(Long userId)
    {
        Long queryUserId = userId == null ? ApiContext.getUserId() : userId;
        followService.checkQueryUser(queryUserId);
        startPage();
        List<Follow> list = followService.selectFansList(queryUserId);
        return getDataTable(list);
    }

    /** 统一把服务层的 {active, fansCount} 塞进 AjaxResult（成功码 0） */
    private AjaxResult toResult(Map<String, Object> result)
    {
        return AjaxResult.success()
                .put("active", result.get("active"))
                .put("fansCount", result.get("fansCount"));
    }
}

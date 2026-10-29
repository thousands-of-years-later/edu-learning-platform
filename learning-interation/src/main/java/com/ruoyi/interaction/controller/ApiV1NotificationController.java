package com.ruoyi.interaction.controller;

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
import com.ruoyi.interaction.domain.Notification;
import com.ruoyi.interaction.service.INotificationService;

/**
 * 通知 接口 v1（Token 认证，给小程序用）
 *
 * 与 ApiNotificationController（/api/notification）功能一一对应，
 * 差别只在【用户ID从哪来】：
 *   老的走 Shiro 会话：/api/** 默认要求登录，用户ID取 ShiroUtils.getUserId()；
 *   这里走 Token：/api/v1/** 在 ShiroConfig 里是 anon，Shiro 没有登录态，
 *   用户ID取自 ApiContext（由 ApiTokenFilter 校验 Token 后写入）。
 *
 * 两个入口同时存在：老客户端不用改，新客户端用 /api/v1。
 * 这里【不加】@Anonymous 注解 —— /api/v1/** 是整体放行的，真正的鉴权由 ApiTokenFilter 做，
 * 标上 @Anonymous 反而会让人误以为这个接口不需要登录。
 *
 * 用户ID一律取上下文，绝不接受前端传入 —— 收件箱、已读都只可能是「我自己的」。
 *
 * 通知是【用户对用户】的收件箱（谁赞了我/收藏了我/评论了我/关注了我），
 * 与后台的「通知公告」（/system/notice）是两套东西，不要看混。
 *
 * @author edu
 */
@RestController
@RequestMapping("/api/v1/notification")
public class ApiV1NotificationController extends BaseController
{
    @Autowired
    private INotificationService notificationService;

    /**
     * 我的通知列表（分页）
     *
     * @param isRead 读取状态筛选（可选：0未读 1已读，不传 = 全部）
     * @param type   通知类型（可选）：1点赞 / 2收藏 / 3评论（含回复）/ 4关注；不传 = 全部
     *               对应设计稿 09_通知页 的四个 tab
     * @return {rows: [Notification], total}（分页参数 pageNum / pageSize）；
     *         rows 里 fromUserName / fromUserAvatar 是触发者的展示信息
     */
    @GetMapping("/list")
    public TableDataInfo list(String isRead, String type)
    {
        // 校验必须在 startPage() 之前：PageHelper 的分页参数挂在 ThreadLocal 上，
        // 抛异常时若已 startPage()，参数不会被消费、可能泄漏给同线程的下一个请求
        notificationService.checkQuery(isRead);
        notificationService.checkType(type);
        startPage();
        List<Notification> list = notificationService.selectMyList(ApiContext.getUserId(), isRead, type);
        return getDataTable(list);
    }

    /**
     * 我的未读数（角标）
     *
     * @return {count: 未读通知数}
     */
    @GetMapping("/unreadCount")
    public AjaxResult unreadCount()
    {
        return AjaxResult.success().put("count", notificationService.countUnread(ApiContext.getUserId()));
    }

    /**
     * 标记单条已读（幂等：已读过的、不是自己的都返回成功）
     *
     * @param notificationId 通知ID
     * @return {count: 操作后的最新未读数}
     */
    @PostMapping("/read")
    public AjaxResult read(Long notificationId)
    {
        notificationService.markRead(ApiContext.getUserId(), notificationId);
        // 【不能】用 toAjax(影响行数)：影响 0 行在幂等语义下是正常结果（已读/非本人），不是错误。
        // 返回最新未读数，客户端标记完顺手更新角标，不用再拉一次
        return AjaxResult.success().put("count", notificationService.countUnread(ApiContext.getUserId()));
    }

    /**
     * 标记单条已读（JSON 请求体）
     *
     * @param body 请求体，字段同表单：notificationId
     */
    @PostMapping(value = "/read", consumes = MediaType.APPLICATION_JSON_VALUE)
    public AjaxResult readJson(@RequestBody Map<String, String> body)
    {
        return read(Convert.toLong(body.get("notificationId")));
    }

    /**
     * 全部标记已读（幂等：没有未读时也返回成功）
     *
     * @return {count: 0，操作后必然没有未读}
     */
    @PostMapping("/readAll")
    public AjaxResult readAll()
    {
        notificationService.markReadAll(ApiContext.getUserId());
        return AjaxResult.success().put("count", notificationService.countUnread(ApiContext.getUserId()));
    }
}

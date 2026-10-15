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
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.common.core.text.Convert;
import com.ruoyi.common.utils.ShiroUtils;
import com.ruoyi.interaction.domain.Notification;
import com.ruoyi.interaction.service.INotificationService;

/**
 * 通知 接口（给小程序用）
 *
 * 通知是【用户对用户】的收件箱（谁赞了我/收藏了我/评论了我/关注了我），
 * 与后台的「通知公告」（/system/notice，管理员发公告）是两套东西，不要看混。
 *
 * 路径放在 /api 下，因此【默认就要求登录】—— ShiroConfig 只放行了
 * /api/login 与 /api/register，这个前缀天然满足「通知必须先登录」，不用改过滤链。
 *
 * 用户ID一律取登录态，绝不接受前端传入 —— 收件箱、已读都只可能是「我自己的」。
 *
 * 每个写接口都提供【表单】与【JSON】两个出口，靠 consumes 区分（与 ApiCommentController 同一写法）：
 * 表单版是裸参数，JSON 版接 Map 后转调表单版，避免两套逻辑各写一遍。
 * readAll 没有参数，表单/JSON 都能打到同一个映射，所以不需要 JSON 出口。
 *
 * @author edu
 */
@RestController
@RequestMapping("/api/notification")
public class ApiNotificationController extends BaseController
{
    @Autowired
    private INotificationService notificationService;

    /**
     * 我的通知列表（分页）
     *
     * @param isRead 读取状态筛选（可选：0未读 1已读，不传 = 全部）
     * @return {rows: [Notification], total}（分页参数 pageNum / pageSize）；
     *         rows 里 fromUserName / fromUserAvatar 是触发者的展示信息
     */
    @GetMapping("/list")
    public TableDataInfo list(String isRead)
    {
        // 校验必须在 startPage() 之前：PageHelper 的分页参数挂在 ThreadLocal 上，
        // 抛异常时若已 startPage()，参数不会被消费、可能泄漏给同线程的下一个请求
        notificationService.checkQuery(isRead);
        startPage();
        List<Notification> list = notificationService.selectMyList(ShiroUtils.getUserId(), isRead);
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
        return AjaxResult.success().put("count", notificationService.countUnread(ShiroUtils.getUserId()));
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
        notificationService.markRead(ShiroUtils.getUserId(), notificationId);
        // 【不能】用 toAjax(影响行数)：影响 0 行在幂等语义下是正常结果（已读/非本人），不是错误。
        // 返回最新未读数，客户端标记完顺手更新角标，不用再拉一次
        return AjaxResult.success().put("count", notificationService.countUnread(ShiroUtils.getUserId()));
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
        notificationService.markReadAll(ShiroUtils.getUserId());
        return AjaxResult.success().put("count", notificationService.countUnread(ShiroUtils.getUserId()));
    }
}

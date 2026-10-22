package com.ruoyi.interaction.controller;

import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.ruoyi.common.core.api.ApiContext;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.common.core.text.Convert;
import com.ruoyi.interaction.domain.MyInteraction;
import com.ruoyi.interaction.service.IInteractionService;

/**
 * 点赞收藏 接口 v1（Token 认证，给小程序用）
 *
 * 与老的 ApiInteractionController（/api/interaction）功能一一对应，
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
 * @author edu
 */
@RestController
@RequestMapping("/api/v1/interaction")
public class ApiV1InteractionController extends BaseController
{
    @Autowired
    private IInteractionService interactionService;

    /**
     * 点赞/取消 或 收藏/取消
     *
     * @param targetType 目标类型（1帖子 2课程 3短视频）
     * @param targetId   目标ID
     * @param type       行为类型（1点赞 2收藏）
     * @return {active: 操作后是否已点赞/已收藏, count: 操作后的真实数量}
     */
    @PostMapping("/toggle")
    public AjaxResult toggle(String targetType, Long targetId, String type)
    {
        Map<String, Object> result = interactionService.toggle(ApiContext.getUserId(), targetType, targetId, type);
        AjaxResult ajax = AjaxResult.success();
        ajax.put("active", result.get("active"));
        ajax.put("count", result.get("count"));
        return ajax;
    }

    /**
     * 批量查询当前用户已点赞/已收藏的目标ID（供列表页一次性回填按钮状态）
     *
     * @param targetType 目标类型
     * @param type       行为类型
     * @param targetIds  目标ID，逗号分隔
     * @return {ids: [已点赞/已收藏的目标ID]}
     */
    @GetMapping("/status")
    public AjaxResult status(String targetType, String type, String targetIds)
    {
        Long[] ids = Convert.toLongArray(targetIds);
        AjaxResult ajax = AjaxResult.success();
        ajax.put("ids", interactionService.activeIds(ApiContext.getUserId(), targetType, type, ids));
        return ajax;
    }

    /**
     * 查某个目标的真实互动数
     *
     * 与老接口 /api/interaction/count 的差别：老接口当初是复制粘贴，
     * 返回的是 {ids:[...]} 而不是数量；v1 按语义返回 {count: n}。
     * 老接口一行没动，免得影响已经在用的客户端。
     *
     * @param targetType 目标类型
     * @param targetId   目标ID
     * @param type       行为类型
     * @return {count: 数量}
     */
    @GetMapping("/count")
    public AjaxResult count(String targetType, Long targetId, String type)
    {
        AjaxResult ajax = AjaxResult.success();
        ajax.put("count", interactionService.count(targetType, targetId, type));
        return ajax;
    }

    /**
     * 我的点赞（设计稿 23_我的点赞）
     *
     * @param targetType 可选过滤：post（文章）/ course（课程）/ shortVideo（短视频）；不传 = 全部
     * @return {total, rows:[{interactionId, targetType, targetId, title, coverUrl, author, createTime}]}
     *         文章没有封面（coverUrl 为 null），显示标题 + 作者；课程/短视频显示封面 + 标题
     */
    @GetMapping("/myLikes")
    public TableDataInfo myLikes(String targetType)
    {
        // 校验放在 startPage() 之前：PageHelper 的分页参数挂在 ThreadLocal 上，
        // 非法参数若在之后抛出，分页参数会残留到本线程的下一条查询
        interactionService.checkQuery(targetType);
        startPage();
        List<MyInteraction> list = interactionService.myLikes(ApiContext.getUserId(), targetType);
        return getDataTable(list);
    }

    /**
     * 我的收藏（设计稿 22_我的收藏），参数与返回同 /myLikes
     */
    @GetMapping("/myFavorites")
    public TableDataInfo myFavorites(String targetType)
    {
        interactionService.checkQuery(targetType);
        startPage();
        List<MyInteraction> list = interactionService.myFavorites(ApiContext.getUserId(), targetType);
        return getDataTable(list);
    }
}

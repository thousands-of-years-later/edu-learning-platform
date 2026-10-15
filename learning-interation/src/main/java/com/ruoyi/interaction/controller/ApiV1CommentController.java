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
import com.ruoyi.interaction.domain.Comment;
import com.ruoyi.interaction.service.ICommentService;

/**
 * 评论 接口 v1（Token 认证，给小程序用）
 *
 * 与老的 ApiCommentController（/api/comment）功能一一对应，
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
 * 每个写接口都提供【表单】与【JSON】两个出口，靠 consumes 区分（与 ApiAuthController 同一写法）。
 *
 * 注意：列表是本项目第一个分页的 /api/v1 接口，客户端要按 pageNum / pageSize 传参
 * （PageHelper 从请求参数里读，缺省是 1 / 10）。
 *
 * @author edu
 */
@RestController
@RequestMapping("/api/v1/comment")
public class ApiV1CommentController extends BaseController
{
    @Autowired
    private ICommentService commentService;

    /**
     * 发表评论
     *
     * @param targetType 目标类型（1帖子 2课程 3短视频）
     * @param targetId   目标ID（课程评论传课程ID，不是课程内视频的ID）
     * @param content    评论内容（纯文本）
     * @return {commentId: 新评论ID, count: 该目标的最新评论总数}
     */
    @PostMapping("/add")
    public AjaxResult add(String targetType, Long targetId, String content)
    {
        Map<String, Object> result = commentService.insertComment(ApiContext.getUserId(), targetType, targetId, content);
        return AjaxResult.success().put("commentId", result.get("commentId")).put("count", result.get("count"));
    }

    /**
     * 发表评论（JSON 请求体）
     *
     * @param body 请求体，字段同表单：targetType、targetId、content
     */
    @PostMapping(value = "/add", consumes = MediaType.APPLICATION_JSON_VALUE)
    public AjaxResult addJson(@RequestBody Map<String, String> body)
    {
        return add(body.get("targetType"), Convert.toLong(body.get("targetId")), body.get("content"));
    }

    /**
     * 评论列表（分页）
     *
     * @param targetType 目标类型（1帖子 2课程 3短视频）
     * @param targetId   目标ID
     * @return {rows: [评论], total: 该目标的评论总数}（分页参数 pageNum / pageSize）
     */
    @GetMapping("/list")
    public TableDataInfo list(String targetType, Long targetId)
    {
        // 校验必须在 startPage() 之前：PageHelper 的分页参数挂在 ThreadLocal 上，
        // 会被它之后的第一条查询消费，而校验里就有查询（见 ICommentService.checkTarget）
        commentService.checkTarget(targetType, targetId);
        startPage();
        List<Comment> list = commentService.selectCommentList(targetType, targetId);
        return getDataTable(list);
    }

    /**
     * 删除自己发表的评论
     *
     * 只收 commentId：目标类型/ID 由后端从评论本身取，
     * 免得客户端删自己的评论却去减别人的计数。
     *
     * @param commentId 评论ID
     * @return {count: 该目标的最新评论总数}
     */
    @PostMapping("/remove")
    public AjaxResult remove(Long commentId)
    {
        Map<String, Object> result = commentService.deleteComment(ApiContext.getUserId(), commentId);
        return AjaxResult.success().put("count", result.get("count"));
    }

    /**
     * 删除自己发表的评论（JSON 请求体）
     *
     * @param body 请求体，字段同表单：commentId
     */
    @PostMapping(value = "/remove", consumes = MediaType.APPLICATION_JSON_VALUE)
    public AjaxResult removeJson(@RequestBody Map<String, String> body)
    {
        return remove(Convert.toLong(body.get("commentId")));
    }
}
